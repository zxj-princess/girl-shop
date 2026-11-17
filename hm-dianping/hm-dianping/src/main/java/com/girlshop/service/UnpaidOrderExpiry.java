package com.girlshop.service;

import com.girlshop.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class UnpaidOrderExpiry {
    @Resource private IOrderService orders;
    @Value("${orders.unpaid-timeout-minutes:30}") private long timeoutMinutes;
    @Scheduled(fixedDelayString = "${orders.expiry-scan-ms:60000}")
    public void scan() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        try {
            List<Order> expired = orders.query().eq("status", 0).le("create_time", cutoff)
                    .orderByAsc("create_time").last("LIMIT 100").list();
            for (Order order : expired) {
                try { orders.expireUnpaid(order.getId(), cutoff); }
                catch (RuntimeException e) { log.warn("关闭超时订单失败 id={}", order.getId(), e); }
            }
        } catch (RuntimeException e) { log.warn("扫描超时订单失败", e); }
    }
}
