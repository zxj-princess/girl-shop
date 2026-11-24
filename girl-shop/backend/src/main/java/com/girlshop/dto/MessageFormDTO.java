package com.girlshop.dto;

import lombok.Data;

/**
 * 留言咨询表单
 */
@Data
public class MessageFormDTO {
    /**
     * 商品id
     */
    private Long productId;
    /**
     * 留言内容
     */
    private String content;
}