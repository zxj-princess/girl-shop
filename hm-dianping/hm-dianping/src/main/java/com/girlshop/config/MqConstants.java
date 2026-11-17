package com.girlshop.config;

/**
 * RabbitMQ 常量
 */
public class MqConstants {
    // ---------- 捡漏订单（原有） ----------
    public static final String FLASH_EXCHANGE = "flash.direct";
    public static final String FLASH_QUEUE = "flash.order.queue";
    public static final String FLASH_ROUTING_KEY = "flash.order";

    // ---------- 卖家上新通知（新增） ----------
    /** 上新通知交换机（direct） */
    public static final String NOTICE_EXCHANGE = "notice.direct";
    /** 上新通知队列 */
    public static final String NOTICE_QUEUE = "notice.new.product.queue";
    /** 上新通知路由键 */
    public static final String NOTICE_ROUTING_KEY = "notice.new.product";
}
