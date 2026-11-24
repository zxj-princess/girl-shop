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
 * 商品留言咨询表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_message")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品id
     */
    private Long productId;

    /**
     * 留言用户id
     */
    private Long userId;

    /**
     * 留言内容
     */
    private String content;

    /**
     * 卖家回复
     */
    private String reply;

    /**
     * 留言时间
     */
    private LocalDateTime createTime;

    /**
     * 回复时间
     */
    private LocalDateTime replyTime;

    // 附加展示字段
    @TableField(exist = false)
    private String nickName;

    @TableField(exist = false)
    private String icon;
}