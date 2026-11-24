package com.girlshop.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girlshop.dto.NewProductNoticeMessage;
import com.girlshop.config.MqConstants;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
@Service @DependsOn("prioritySchema")
public class NoticeDeliveryService {
 @org.springframework.beans.factory.annotation.Value("${platform.workers.enabled:true}") private boolean improvementWorkers=true;

 @Resource private JdbcTemplate jdbc; @Resource private RabbitTemplate rabbit; @Resource private ObjectMapper json;
 @Transactional public void enqueue(NewProductNoticeMessage message){try{jdbc.update("INSERT INTO tb_notice_outbox(product_id,payload) VALUES(?,?) ON DUPLICATE KEY UPDATE product_id=product_id",message.getProductId(),json.writeValueAsString(message));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("通知快照失败",e);}}
 @Scheduled(fixedDelayString="${notice.dispatch-ms:5000}") public void dispatch(){if(!improvementWorkers)return;
  for(Map<String,Object> row:jdbc.queryForList("SELECT product_id,payload,attempts FROM tb_notice_outbox WHERE status='PENDING' AND next_at<=NOW() ORDER BY next_at LIMIT 10")){
   long id=((Number)row.get("product_id")).longValue();int attempts=((Number)row.get("attempts")).intValue();
   if(jdbc.update("UPDATE tb_notice_outbox SET status='SENDING',attempts=attempts+1,next_at=DATE_ADD(NOW(),INTERVAL 60 SECOND) WHERE product_id=? AND status='PENDING' AND next_at<=NOW()",id)!=1)continue;
   try{CorrelationData c=new CorrelationData(UUID.randomUUID().toString());rabbit.convertAndSend(MqConstants.NOTICE_EXCHANGE,MqConstants.NOTICE_ROUTING_KEY,json.readValue(row.get("payload").toString(),NewProductNoticeMessage.class),m->{m.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);return m;},c);if(!c.getFuture().get(3,TimeUnit.SECONDS).isAck()||c.getReturned()!=null)throw new IllegalStateException();jdbc.update("UPDATE tb_notice_outbox SET status='SENT',last_error=NULL WHERE product_id=? AND status='SENDING'",id);}catch(Exception e){failed(id,attempts+1,e.getClass().getSimpleName());}
  }
  jdbc.update("UPDATE tb_notice_outbox SET status=IF(attempts>=8,'DEAD','PENDING'),last_error='DELIVERY_INTERRUPTED' WHERE status='SENDING' AND next_at<NOW()");
 }
 public void failed(long id,int attempts,String type){jdbc.update("UPDATE tb_notice_outbox SET status=?,last_error=?,next_at=DATE_ADD(NOW(),INTERVAL 60 SECOND) WHERE product_id=?",attempts>=8?"DEAD":"PENDING",type,id);}
 @Transactional public void consumerFailed(NewProductNoticeMessage message){enqueue(message);Integer attempts=jdbc.queryForObject("SELECT attempts FROM tb_notice_outbox WHERE product_id=? FOR UPDATE",Integer.class,message.getProductId());failed(message.getProductId(),attempts,"CONSUMER_FAILED");}
}
