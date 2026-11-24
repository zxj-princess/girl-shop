package com.girlshop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@DependsOn("prioritySchema")
@ConditionalOnProperty(name="flash.worker.enabled",havingValue="true",matchIfMissing=true)
@Slf4j
public class FlashOutboxWorker {
    @Resource private JdbcTemplate jdbc;
    @Resource private RabbitTemplate rabbit;
    @Resource private FlashRequestService requests;
    @org.springframework.beans.factory.annotation.Value("${flash.queue:flash.request.v2.queue}") private String queue;
    private long reconcileCursor=0;
    @Scheduled(fixedDelayString="${flash.dispatch-ms:1000}")
    public void dispatch() {
        for(Long id:jdbc.queryForList("SELECT id FROM tb_flash_request WHERE status='PENDING' AND next_publish_at<=NOW() AND deadline_at>NOW() ORDER BY created_at LIMIT 10",Long.class)) {
            // Durable next-attempt timestamp also throttles failures across process restarts.
            if(jdbc.update("UPDATE tb_flash_request SET next_publish_at=DATE_ADD(NOW(),INTERVAL 10 SECOND),publish_attempts=publish_attempts+1 WHERE id=? AND status='PENDING' AND next_publish_at<=NOW()",id)!=1)continue;
            try {
                CorrelationData correlation=new CorrelationData(UUID.randomUUID().toString());
                rabbit.convertAndSend("",queue,id.toString(),message->{
                    message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT); return message;
                },correlation);
                if(!correlation.getFuture().get(3,TimeUnit.SECONDS).isAck() || correlation.getReturned()!=null)
                    throw new IllegalStateException("消息未送达");
            }catch(Exception e){log.warn("捡漏请求投递失败，将重试 requestId={}",id,e);}
        }
    }
    @Scheduled(fixedDelayString="${flash.reconcile-ms:30000}")
    public void reconcile() {
        for(Long id:jdbc.queryForList("SELECT id FROM tb_flash_request WHERE status='PENDING' AND deadline_at<=NOW() LIMIT 100",Long.class)) {
            try {requests.expire(id);} catch(RuntimeException e){log.warn("捡漏请求补偿失败 id={}",id,e);}
        }
        // Include pending requests, not only currently visible flash products.
        List<Long> batch=jdbc.queryForList("SELECT id FROM (SELECT id FROM tb_product WHERE is_flash=1 UNION SELECT product_id AS id FROM tb_flash_request WHERE status='PENDING') candidates WHERE id>? ORDER BY id LIMIT 100",Long.class,reconcileCursor);
        for(Long id:batch) {
            try{requests.reconcile(id);}catch(RuntimeException e){log.warn("捡漏库存对账失败 productId={}",id,e);}
        }
        reconcileCursor=batch.size()<100?0:batch.get(batch.size()-1);
    }
}
