package com.girlshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 捡漏订单消息（用于 RabbitMQ 异步下单）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单id（全局id）
     */
    private Long orderId;

    /**
     * 商品id
     */
    private Long productId;

    /**
     * 用户id（买家）
     */
    private Long userId;

    /**
     * 指定的收货地址id（可选，为空则用用户默认地址）
     */
    private Long addressId;
}
