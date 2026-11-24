package com.girlshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * 二手服装商品表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 发布人（卖家）id
     */
    private Long userId;

    /**
     * 分类id
     */
    private Long categoryId;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 品牌
     */
    private String brand;

    /**
     * 尺码
     */
    private String size;

    /** 实测信息及瑕疵说明，历史商品允许为空。 */
    private String measurements;
    private String defects;

    /**
     * 成色 1全新 2九成新 3八成新 4七成新 5六成以下 6九九新 7九五新
     * 注意：condition 是 MySQL 保留字，列名必须加反引号，否则 SQL 语法错误
     */
    @TableField("`condition`")
    private Integer condition;

    /**
     * 售价
     */
    private BigDecimal price;

    /**
     * 原价
     */
    private BigDecimal originalPrice;

    /**
     * 多图片，逗号分隔
     */
    private String images;

    /**
     * 状态 0下架 1在售 2已售出
     */
    private Integer status;

    /**
     * 是否捡漏 0否 1是
     */
    private Integer isFlash;

    /**
     * 捡漏价
     */
    private BigDecimal flashPrice;

    /**
     * 捡漏开始时间
     */
    private LocalDateTime flashBeginTime;

    /**
     * 捡漏结束时间
     */
    private LocalDateTime flashEndTime;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 销量
     */
    private Integer sold;

    /**
     * 浏览量
     */
    private Integer views;

    @JsonIgnore
    private LocalDateTime createTime;

    @JsonIgnore
    private LocalDateTime updateTime;

    // ---------- 以下为展示附加字段（非数据库字段） ----------

    @TableField(exist = false)
    private Long favoriteCount;
    @TableField(exist = false)
    private String sellerNickName;

    @TableField(exist = false)
    private String sellerIcon;

    @TableField(exist = false)
    private String categoryName;
}
