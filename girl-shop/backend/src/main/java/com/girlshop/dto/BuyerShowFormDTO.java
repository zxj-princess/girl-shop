package com.girlshop.dto;

import lombok.Data;

import java.util.List;

/**
 * 买家秀发布表单
 */
@Data
public class BuyerShowFormDTO {
    /**
     * 订单id（必须为已完成的订单）
     */
    private Long orderId;
    /**
     * 买家秀图片列表
     */
    private List<String> images;
    /**
     * 买家秀文字描述
     */
    private String content;
}
