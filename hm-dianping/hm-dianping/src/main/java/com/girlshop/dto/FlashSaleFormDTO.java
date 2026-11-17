package com.girlshop.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设置捡漏表单
 */
@Data
public class FlashSaleFormDTO {
    /**
     * 捡漏库存
     */
    private Integer stock;
    /**
     * 捡漏价
     */
    private BigDecimal flashPrice;
    /**
     * 开始时间
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;
    /**
     * 结束时间
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
