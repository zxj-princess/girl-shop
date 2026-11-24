package com.girlshop.mq;

import com.girlshop.config.MqConstants;
import com.girlshop.dto.NewProductNoticeMessage;
import com.girlshop.service.IUserNoticeService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 卖家上新通知消费者：
 * 监听上新队列，异步查询卖家粉丝并批量写入消息通知表
 * 发布商品接口本身只管发消息，不被粉丝数量阻塞
 */
@Slf4j
@Component
public class NewProductNoticeListener {

    @Resource
    private IUserNoticeService userNoticeService;
    @Resource private com.girlshop.service.NoticeDeliveryService delivery;

    @RabbitListener(queues = MqConstants.NOTICE_QUEUE)
    public void onNewProduct(NewProductNoticeMessage message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("收到上新通知消息：{}", message);
        try {
            // 异步为该卖家的所有粉丝生成上新通知
            userNoticeService.createNoticesForFollowers(message);
            // 手动确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("上新通知处理失败，保存受控重试任务", e);
            // Commit durable retry state before acknowledging; replay is bounded and idempotent.
            delivery.consumerFailed(message);
            channel.basicAck(deliveryTag,false);
        }
    }
}
