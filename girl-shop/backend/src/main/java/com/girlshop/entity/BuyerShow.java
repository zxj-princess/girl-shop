package com.girlshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 买家秀表：交易完成后买家晒单
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_buyer_show")
public class BuyerShow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 商品id
     */
    private Long productId;

    /**
     * 买家id（发布人）
     */
    private Long userId;

    /**
     * 卖家id
     */
    private Long sellerId;

    /**
     * 买家秀图片，逗号分隔
     */
    private String images;

    /**
     * 买家秀文字描述
     */
    private String content;

    private LocalDateTime createTime;

    // ---------- 展示附加字段（非数据库字段） ----------
    @TableField(exist = false)
    private String nickName;

    @TableField(exist = false)
    private String icon;
}
