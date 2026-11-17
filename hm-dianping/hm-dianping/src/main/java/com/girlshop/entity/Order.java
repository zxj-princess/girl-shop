package com.girlshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_order")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（全局id）
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 买家id
     */
    private Long buyerId;

    /**
     * 卖家id
     */
    private Long sellerId;

    /**
     * 商品id
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

    /**
     * 成交单价
     */
    private BigDecimal price;

    /**
     * 实付金额
     */
    private BigDecimal payAmount;

    /**
     * 收货人姓名快照
     */
    private String receiverName;

    /**
     * 收货人手机号快照
     */
    private String receiverPhone;

    /**
     * 收货地址快照
     */
    private String receiverAddress;

    /**
     * 状态 0待付款 1待发货 2待收货 3已完成 4已关闭
     */
    private Integer status;
    @com.baomidou.mybatisplus.annotation.TableField(exist=false)
    private Boolean reviewed;
    @com.baomidou.mybatisplus.annotation.TableField(exist=false)
    private Boolean canReview;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    private LocalDateTime receiveTime;

    private LocalDateTime finishTime;

    private LocalDateTime closeTime;

    @JsonIgnore
    private LocalDateTime updateTime;
}
