package com.girlshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@EnableRabbit
@org.springframework.scheduling.annotation.EnableScheduling
@MapperScan("com.girlshop.mapper")
@SpringBootApplication
public class GirlShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(GirlShopApplication.class, args);
    }

}
