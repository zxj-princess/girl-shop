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
 * 卖家评价表：订单完成后买家评价卖家
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_seller_comment")
public class SellerComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 被评价的卖家id
     */
    private Long sellerId;

    /**
     * 评价人id（买家）
     */
    private Long userId;

    /**
     * 星级评分 1~5
     */
    private Integer score;

    /**
     * 评价内容
     */
    private String content;

    private LocalDateTime createTime;

    // ---------- 展示附加字段（非数据库字段） ----------
    @TableField(exist = false)
    private String nickName;

    @TableField(exist = false)
    private String icon;

    /**
     * 该卖家的平均评分（展示用，非数据库字段）
     */
    @TableField(exist = false)
    private Double avgScore;
}
