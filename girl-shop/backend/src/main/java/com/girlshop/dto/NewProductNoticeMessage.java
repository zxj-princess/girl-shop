package com.girlshop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 卖家上新通知 MQ 消息体
 * - 卖家发布新商品后，ProductServiceImpl 发送此消息
 * - NewProductNoticeListener 消费后查询粉丝并批量写入 tb_user_notice
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewProductNoticeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 卖家id
     */
    private Long sellerId;
    /**
     * 新商品id
     */
    private Long productId;
    /**
     * 商品标题快照
     */
    private String productTitle;
    /**
     * 商品首图快照
     */
    private String productImage;
}
