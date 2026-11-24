package com.girlshop.dto;

import lombok.Data;

/**
 * 评价表单
 */
@Data
public class ReviewFormDTO {
    /**
     * 订单id
     */
    private Long orderId;
    /**
     * 评分 1~5
     */
    private Integer score;
    /**
     * 评价内容
     */
    private String content;
    private java.util.List<String> images;
}
