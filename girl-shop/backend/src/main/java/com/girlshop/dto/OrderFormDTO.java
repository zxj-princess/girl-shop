package com.girlshop.dto;

import lombok.Data;

/**
 * 创建订单表单
 */
@Data
public class OrderFormDTO {
    /**
     * 商品id
     */
    private Long productId;
    /**
     * 收货地址id
     */
    private Long addressId;
}