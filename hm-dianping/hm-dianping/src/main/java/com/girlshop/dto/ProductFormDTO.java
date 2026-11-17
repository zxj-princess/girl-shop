package com.girlshop.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发布/编辑商品表单
 */
@Data
public class ProductFormDTO {
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
     * 成色 1全新 2九成新 3八成新 4七成新及以下
     */
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
     * 图片列表
     */
    private List<String> images;
}