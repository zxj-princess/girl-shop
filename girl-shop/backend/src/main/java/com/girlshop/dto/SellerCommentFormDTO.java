package com.girlshop.dto;

import lombok.Data;

/**
 * 卖家评价发布表单
 */
@Data
public class SellerCommentFormDTO {
    /**
     * 订单id（必须为该卖家的已完成订单）
     */
    private Long orderId;
    /**
     * 星级评分 1~5
     */
    private Integer score;
    /**
     * 评价内容
     */
    private String content;
}
