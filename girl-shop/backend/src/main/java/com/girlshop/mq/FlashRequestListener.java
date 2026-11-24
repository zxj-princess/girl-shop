package com.girlshop.mq;

import com.girlshop.service.FlashRequestService;
import com.rabbitmq.client.Channel;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import javax.annotation.Resource;
import java.io.IOException;

@Component
@ConditionalOnProperty(name="flash.worker.enabled",havingValue="true",matchIfMissing=true)
public class FlashRequestListener {
    @Resource private FlashRequestService requests;
    @RabbitListener(queues="${flash.queue:flash.request.v2.queue}")
    public void receive(String body,Channel channel,@Header(AmqpHeaders.DELIVERY_TAG)long tag)throws IOException {
        Long id;
        try{id=Long.valueOf(body);}catch(NumberFormatException e){channel.basicReject(tag,false);return;}
        try {requests.complete(id);}
        catch(RuntimeException e){
            try{requests.failedAttempt(id,"订单创建失败");}
            // The durable outbox will retry after DB recovery; park this delivery instead of a hot requeue loop.
            catch(RuntimeException storageFailure){channel.basicReject(tag,false);return;}
        }
        // Acknowledgement follows either committed success or committed retry/compensation.
        channel.basicAck(tag,false);
    }
}
