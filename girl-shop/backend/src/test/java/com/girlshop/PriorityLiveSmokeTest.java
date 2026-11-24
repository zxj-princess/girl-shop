package com.girlshop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Checks running local APIs with an ephemeral user-0 session; never accesses an existing user's session. */
@EnabledIfSystemProperty(named="priority.live",matches="true")
class PriorityLiveSmokeTest {
    @Test void runningOrderPagesAndNewRequestApi() {
        LettuceConnectionFactory cf=new LettuceConnectionFactory("127.0.0.1",6379);cf.afterPropertiesSet();
        StringRedisTemplate redis=new StringRedisTemplate(cf);
        String token="priority-smoke-"+UUID.randomUUID(),key="login:token:"+token;
        try{
            Map<String,String> profile=new HashMap<>();profile.put("id","0");profile.put("nickName","本机接口检查");
            redis.opsForHash().putAll(key,profile);redis.expire(key,60,TimeUnit.SECONDS);
            HttpHeaders headers=new HttpHeaders();headers.set("authorization",token);
            RestTemplate rest=new RestTemplate();
            for(String path:Arrays.asList("/api/order/buy","/api/order/sell","/api/order/my?page=1&pageSize=10","/api/order/flash/requests","/api/trade/notices","/api/trade/unread","/api/trade/contacts","/api/trade/blocks","/api/trade/cases","/api/trade/reports","/api/trade/drafts","/api/trade/message-counts","/api/trade/inbox")) {
                ResponseEntity<Map> result=rest.exchange("http://127.0.0.1:8090"+path,HttpMethod.GET,new HttpEntity<>(headers),Map.class);
                assertEquals(Boolean.TRUE,result.getBody().get("success"),path);
            }
            for(String page:Arrays.asList("mine.html","orders.html")) {
                String html=rest.getForObject("http://127.0.0.1:8090/"+page,String.class);
                assertTrue(html.contains("flash-requests") || html.contains("location.replace('mine.html#orders')"));
            }
            String sellerId=System.getProperty("seller.smoke.id");
            if(sellerId!=null) {
                assertTrue(sellerId.matches("[1-9][0-9]*"));
                Map sellerProfile=rest.exchange("http://127.0.0.1:8090/api/seller/"+sellerId,HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody();
                assertEquals(Boolean.TRUE,sellerProfile.get("success"));assertFalse(((Map)sellerProfile.get("data")).containsKey("phone"));
                Map listing=rest.exchange("http://127.0.0.1:8090/api/seller/"+sellerId+"/products",HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody();
                assertEquals(Boolean.TRUE,listing.get("success"));
                Map picker=rest.exchange("http://127.0.0.1:8090/api/trade/peers/"+sellerId+"/products?status=-1",HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody();
                assertEquals(Boolean.TRUE,picker.get("success"));
                if(!((List)listing.get("data")).isEmpty()){
                    Object product=((Map)((List)listing.get("data")).get(0)).get("id");
                    Map context=rest.exchange("http://127.0.0.1:8090/api/trade/peers/"+sellerId+"/product-context/"+product,HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody();
                    assertEquals(Boolean.TRUE,context.get("success"));assertEquals(Boolean.FALSE,((Map)context.get("data")).get("sent"));
                }
                for(Object item:(List)listing.get("data"))assertEquals(sellerId,((Map)item).get("userId"));
                assertTrue(rest.getForObject("http://127.0.0.1:8090/seller.html?id="+sellerId,String.class).contains("seller-products"));
            }
            String ticket=(String)rest.exchange("http://127.0.0.1:8090/api/trade/realtime-ticket",HttpMethod.POST,new HttpEntity<>(headers),Map.class).getBody().get("data");
            java.util.concurrent.BlockingQueue<String> events=new java.util.concurrent.LinkedBlockingQueue<>();java.util.concurrent.CountDownLatch closed=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger closeCode=new java.util.concurrent.atomic.AtomicInteger();
            org.springframework.web.socket.handler.TextWebSocketHandler probe=new org.springframework.web.socket.handler.TextWebSocketHandler(){
                @Override protected void handleTextMessage(org.springframework.web.socket.WebSocketSession s,org.springframework.web.socket.TextMessage m){events.add(m.getPayload());}
                @Override public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession s,org.springframework.web.socket.CloseStatus status){closeCode.set(status.getCode());closed.countDown();}
            };
            org.springframework.web.socket.WebSocketHttpHeaders wsHeaders=new org.springframework.web.socket.WebSocketHttpHeaders();wsHeaders.setOrigin("http://localhost:8090");
            org.springframework.web.socket.client.standard.StandardWebSocketClient wsClient=new org.springframework.web.socket.client.standard.StandardWebSocketClient();
            java.net.URI socketUri=java.net.URI.create("ws://127.0.0.1:8090/api/ws/messages?ticket="+ticket);
            try(org.springframework.web.socket.WebSocketSession socket=wsClient.doHandshake(probe,wsHeaders,socketUri).get(5,TimeUnit.SECONDS)){
                assertTrue(events.poll(3,TimeUnit.SECONDS).contains("READY"));socket.sendMessage(new org.springframework.web.socket.TextMessage("{\"type\":\"PING\"}"));assertTrue(events.poll(3,TimeUnit.SECONDS).contains("PONG"));
                assertThrows(Exception.class,()->wsClient.doHandshake(new org.springframework.web.socket.handler.TextWebSocketHandler(),wsHeaders,socketUri).get(3,TimeUnit.SECONDS));
                redis.delete(key);socket.sendMessage(new org.springframework.web.socket.TextMessage("{\"type\":\"PING\"}"));assertTrue(closed.await(3,TimeUnit.SECONDS));assertEquals(1008,closeCode.get());
            }
            System.out.println("LIVE_SMOKE: order APIs, nginx WebSocket upgrade, one-use ticket, heartbeat and auth expiry OK");
        }catch(Exception e){throw new IllegalStateException(e);
        }finally{redis.delete(key);cf.destroy();}
    }
}
