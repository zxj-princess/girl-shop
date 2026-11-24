package com.girlshop.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girlshop.config.MqConstants;
import com.girlshop.dto.FlashOrderMessage;
import com.girlshop.service.IOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 捡漏订单消费者：监听队列，异步落库创建订单
 * - 手动反序列化，兼容历史遗留的 Java 原生序列化消息
 * - 遇到无法识别的坏消息直接 ack 丢弃，避免无限重试阻塞队列
 */
@Slf4j
@Component
public class FlashOrderListener {

    @Resource
    private IOrderService orderService;
    @Resource
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = MqConstants.FLASH_QUEUE)
    public void onFlashOrder(Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        byte[] body = message.getBody();
        FlashOrderMessage msg = null;
        try {
            String contentType = message.getMessageProperties() != null
                    ? message.getMessageProperties().getContentType() : null;
            if (contentType != null && contentType.contains("java-serialized")) {
                // 历史遗留：Java 原生序列化的旧消息，直接 ack 丢弃
                log.warn("检测到 Java 原生序列化旧消息，跳过处理并 ack 丢弃");
                channel.basicAck(deliveryTag, false);
                return;
            }
            // 正常 JSON 消息：反序列化后落库
            msg = objectMapper.readValue(body, FlashOrderMessage.class);
            log.info("收到捡漏订单消息：orderId={}, productId={}, userId={}",
                    msg.getOrderId(), msg.getProductId(), msg.getUserId());
            orderService.createFlashOrder(msg);
            channel.basicAck(deliveryTag, false);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // JSON 解析失败：坏消息，直接 ack 丢弃，避免无限重试阻塞队列
            log.error("消息 JSON 解析失败，已 ack 丢弃：{}", e.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 旧请求由数据库事务处理，失败不会留下新的库存扣减。
            log.error("旧捡漏订单处理异常：orderId={}, error={}",
                    msg != null ? msg.getOrderId() : "unknown", e.getMessage());
            if (!Boolean.TRUE.equals(message.getMessageProperties().getRedelivered()) && !(e instanceof IllegalArgumentException)) {
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            // 旧消息最多重试一次。确认失败消息已持久发送后才确认原消息。
            try {
                org.springframework.amqp.rabbit.connection.CorrelationData correlation =
                        new org.springframework.amqp.rabbit.connection.CorrelationData(java.util.UUID.randomUUID().toString());
                message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                rabbitTemplate.send("", "flash.order.failed.queue", message, correlation);
                if (!correlation.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).isAck() || correlation.getReturned() != null)
                    throw new IllegalStateException("失败消息未确认");
                channel.basicAck(deliveryTag, false);
            } catch (Exception publishError) {
                log.error("失败消息转存失败，保留原消息", publishError);
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
