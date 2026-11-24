package com.girlshop.realtime;

import org.springframework.context.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.http.server.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import javax.annotation.Resource;
import java.util.*;

@Configuration @EnableWebSocket
public class RealtimeConfig implements WebSocketConfigurer {
    @Resource private RealtimeHub hub;
    @Resource private RealtimeTickets tickets;
    @Value("${shop.websocket.allowed-origins:http://localhost:8090,http://127.0.0.1:8090}") private String origins;
    @Bean("realtimeExecutor") public static ThreadPoolTaskExecutor realtimeExecutor(){ThreadPoolTaskExecutor e=new ThreadPoolTaskExecutor();e.setCorePoolSize(1);e.setMaxPoolSize(2);e.setQueueCapacity(500);e.setThreadNamePrefix("realtime-");e.setWaitForTasksToCompleteOnShutdown(false);return e;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(hub,"/ws/messages").setAllowedOrigins(origins.split(",")).addInterceptors(new HandshakeInterceptor(){
            @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
                try{String ticket=UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");Map<String,Object> auth=tickets.consume(ticket);if(auth!=null){attributes.putAll(auth);return true;}}catch(RuntimeException ignored){}
                response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;
            }
            @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception error){}
        });
    }
}
