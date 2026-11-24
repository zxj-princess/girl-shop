package com.girlshop.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @org.springframework.beans.factory.annotation.Value("${spring.redis.host:127.0.0.1}") private String host;
    @org.springframework.beans.factory.annotation.Value("${spring.redis.port:6379}") private int port;
    @org.springframework.beans.factory.annotation.Value("${spring.redis.password:}") private String password;
    @org.springframework.beans.factory.annotation.Value("${spring.redis.database:0}") private int database;

    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        org.redisson.config.SingleServerConfig server=config.useSingleServer().setAddress("redis://"+host+":"+port);
        server.setDatabase(database).setConnectTimeout(2000);
        if(password!=null&&!password.isEmpty())server.setPassword(password);
        //创建RedissonClient对象
        return Redisson.create(config);
    }












}
