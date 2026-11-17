package com.girlshop.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import org.springframework.transaction.support.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Qualifier;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/** Single application instance. Persistent HTTP history is the source of truth. */
@Component @Slf4j
public class RealtimeHub extends TextWebSocketHandler {
    @Resource private RealtimeTickets tickets;
    @Resource private ObjectMapper json;
    @Resource @Qualifier("realtimeExecutor") private org.springframework.core.task.TaskExecutor executor;
    private final ConcurrentMap<Long,ConcurrentMap<String,Client>> users=new ConcurrentHashMap<>();
    private final ConcurrentMap<Long,AtomicLong> revisions=new ConcurrentHashMap<>();
    private final String epoch=UUID.randomUUID().toString();
    private static class Client {
        final WebSocketSession socket;final long user;final String token;volatile long seen=System.currentTimeMillis();
        Client(WebSocketSession s){user=((Number)s.getAttributes().get("userId")).longValue();token=(String)s.getAttributes().get("token");socket=new ConcurrentWebSocketSessionDecorator(s,5000,65536);}
    }
    @Override public void afterConnectionEstablished(WebSocketSession session)throws Exception {
        Client client=new Client(session);
        if(!tickets.valid(client.user,client.token)){session.close(CloseStatus.POLICY_VIOLATION);return;}
        java.util.concurrent.atomic.AtomicBoolean accepted=new java.util.concurrent.atomic.AtomicBoolean();
        session.setTextMessageSizeLimit(1024);
        users.compute(client.user,(key,group)->{if(group==null)group=new ConcurrentHashMap<>();if(group.size()<12){group.put(session.getId(),client);accepted.set(true);}return group;});
        if(!accepted.get()){session.close(new CloseStatus(1013,"Retry later"));return;}
        send(client,"READY",Collections.emptyMap());
    }
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage text)throws Exception {
        Client client=find(session);if(client==null)return;
        if(!tickets.valid(client.user,client.token)){close(client,CloseStatus.POLICY_VIOLATION);return;}
        if(!"PING".equals(json.readTree(text.getPayload()).path("type").asText())){close(client,CloseStatus.NOT_ACCEPTABLE);return;}
        client.seen=System.currentTimeMillis();send(client,"PONG",Collections.emptyMap());
    }
    @Override public void handleTransportError(WebSocketSession session,Throwable error){Client client=find(session);if(client!=null)close(client,CloseStatus.SERVER_ERROR);}
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){remove(session);}
    private Client find(WebSocketSession session){Object id=session.getAttributes().get("userId");if(!(id instanceof Number))return null;Map<String,Client> group=users.get(((Number)id).longValue());return group==null?null:group.get(session.getId());}
    private void remove(WebSocketSession session){Object id=session.getAttributes().get("userId");if(id instanceof Number){long user=((Number)id).longValue();users.computeIfPresent(user,(key,group)->{group.remove(session.getId());if(group.isEmpty()){revisions.remove(user);return null;}return group;});}}
    private void close(Client client,CloseStatus status){try{client.socket.close(status);}catch(Exception ignored){}finally{remove(client.socket);}}
    private void send(Client client,String type,Map<String,Object> detail)throws Exception {
        Map<String,Object> event=new LinkedHashMap<>(detail);event.put("type",type);event.put("epoch",epoch);event.put("revision",String.valueOf(revisions.computeIfAbsent(client.user,k->new AtomicLong()).get()));
        client.socket.sendMessage(new TextMessage(json.writeValueAsString(event)));
    }
    public void changed(long user,String type,Map<String,Object> detail){
        Map<String,Object> snapshot=new HashMap<>(detail);
        Runnable action=()->publish(user,type,snapshot);
        if(TransactionSynchronizationManager.isActualTransactionActive()&&TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}});
        else action.run();
    }
    public void changed(long user){changed(user,"STATE_CHANGED",Collections.emptyMap());}
    private void publish(long user,String type,Map<String,Object> detail){
        Map<String,Client> group=users.get(user);if(group==null||group.isEmpty())return;
        revisions.computeIfAbsent(user,k->new AtomicLong()).incrementAndGet();
        try{executor.execute(()->{for(Client client:group.values()){try{if(!tickets.valid(user,client.token)){close(client,CloseStatus.POLICY_VIOLATION);continue;}send(client,type,detail);}catch(Exception e){close(client,CloseStatus.SERVER_ERROR);}}});}
        catch(RuntimeException e){log.warn("实时消息发送队列已满，客户端将重连补拉");for(Client client:group.values())close(client,new CloseStatus(1013,"Retry later"));}
    }
    public void revoke(String token){for(Map<String,Client> group:users.values())for(Client client:group.values())if(client.token.equals(token))close(client,CloseStatus.POLICY_VIOLATION);}
    @Scheduled(fixedDelay=30000) public void sweep(){long now=System.currentTimeMillis();for(Map<String,Client> group:users.values())for(Client client:group.values())if(now-client.seen>70000)close(client,CloseStatus.SESSION_NOT_RELIABLE);}
}
