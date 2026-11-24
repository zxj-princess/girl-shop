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
 * 商品评价表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_review")
public class Review implements Serializable {

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
     * 评价人id（买家）
     */
    private Long userId;

    /**
     * 评分 1~5
     */
    private Integer score;

    /**
     * 评价内容
     */
    private String content;
    private String images;

    /**
     * 评价时间
     */
    private LocalDateTime createTime;

    // 附加展示字段
    @TableField(exist = false)
    private String nickName;

    @TableField(exist = false)
    private String icon;
}
