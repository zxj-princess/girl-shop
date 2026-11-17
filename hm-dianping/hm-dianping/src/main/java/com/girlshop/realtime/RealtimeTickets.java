package com.girlshop.realtime;

import com.girlshop.utils.RedisConstants;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RealtimeTickets {
    @Resource private StringRedisTemplate redis;
    public String issue(long user,String token){
        if(!valid(user,token))throw new IllegalArgumentException("登录已过期");
        String ticket=UUID.randomUUID().toString();
        redis.opsForValue().set("ws:ticket:"+ticket,user+"\n"+token,30,TimeUnit.SECONDS);
        return ticket;
    }
    public Map<String,Object> consume(String ticket){
        if(ticket==null||!ticket.matches("[a-fA-F0-9-]{36}"))return null;
        String value=redis.execute(new DefaultRedisScript<>("local v=redis.call('get',KEYS[1]); if v then redis.call('del',KEYS[1]) end; return v",String.class),Collections.singletonList("ws:ticket:"+ticket));
        if(value==null)return null;
        String[] parts=value.split("\n",2);
        if(parts.length!=2)return null;
        try{long user=Long.parseLong(parts[0]);if(!valid(user,parts[1]))return null;Map<String,Object> auth=new HashMap<>();auth.put("userId",user);auth.put("token",parts[1]);return auth;}catch(NumberFormatException e){return null;}
    }
    public boolean valid(long user,String token){
        if(token==null||token.isEmpty())return false;
        Object id=redis.opsForHash().get(RedisConstants.LOGIN_USER_KEY+token,"id");
        return id!=null&&String.valueOf(user).equals(id.toString());
    }
}
