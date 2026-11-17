package com.girlshop.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * - 声明「捡漏订单」与「上新通知」的交换机、队列、绑定关系
 * - durable=true 持久化，保证 broker 重启后队列不丢失
 * - 使用 Jackson2JsonMessageConverter 做 JSON 序列化，绕过 Spring AMQP 2.4.x 原生反序列化安全限制
 */
@Configuration
public class MqConfig {
    @Bean public Queue flashRequestDeadQueue() { return new Queue("flash.request.v2.dead",true); }
    @Bean public Queue flashRequestQueue() {
        java.util.Map<String,Object> args=new java.util.HashMap<>();
        args.put("x-dead-letter-exchange",""); args.put("x-dead-letter-routing-key","flash.request.v2.dead");
        return new Queue("flash.request.v2.queue",true,false,false,args);
    }
    @Bean
    public Queue failedFlashQueue() {
        return new Queue("flash.order.failed.queue", true);
    }

    /**
     * 统一使用 JSON 消息转换器，替代默认的 SimpleMessageConverter（Java 原生序列化）
     * Spring AMQP 2.4+ 默认禁止反序列化未知类，必须改用 JSON
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---------- 捡漏订单 ----------

    /**
     * 捡漏订单交换机（direct 类型，持久化）
     */
    @Bean
    public DirectExchange flashExchange() {
        return new DirectExchange(MqConstants.FLASH_EXCHANGE, true, false);
    }

    /**
     * 捡漏订单队列（持久化，非独占，不自动删除）
     */
    @Bean
    public Queue flashQueue() {
        return new Queue(MqConstants.FLASH_QUEUE, true);
    }

    /**
     * 将捡漏订单队列绑定到交换机，路由键为 FLASH_ROUTING_KEY
     */
    @Bean
    public Binding flashBinding() {
        return BindingBuilder.bind(flashQueue())
                .to(flashExchange())
                .with(MqConstants.FLASH_ROUTING_KEY);
    }

    // ---------- 上新通知 ----------

    /**
     * 上新通知交换机（direct 类型，持久化）
     */
    @Bean
    public DirectExchange noticeExchange() {
        return new DirectExchange(MqConstants.NOTICE_EXCHANGE, true, false);
    }

    /**
     * 上新通知队列（持久化，非独占，不自动删除）
     */
    @Bean
    public Queue noticeQueue() {
        return new Queue(MqConstants.NOTICE_QUEUE, true);
    }

    /**
     * 将上新通知队列绑定到交换机，路由键为 NOTICE_ROUTING_KEY
     */
    @Bean
    public Binding noticeBinding() {
        return BindingBuilder.bind(noticeQueue())
                .to(noticeExchange())
                .with(MqConstants.NOTICE_ROUTING_KEY);
    }
}
