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
 * 消息通知表：卖家上新异步推送给粉丝
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_notice")
public class UserNotice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 接收通知的粉丝id
     */
    private Long userId;

    /**
     * 上新卖家id
     */
    private Long sellerId;

    /**
     * 新商品id
     */
    private Long productId;

    /**
     * 商品标题快照
     */
    private String title;

    /**
     * 商品首图快照
     */
    private String productImage;

    /**
     * 是否已读 0未读 1已读
     */
    private Integer isRead;

    private LocalDateTime createTime;

    // ---------- 展示附加字段（非数据库字段） ----------
    @TableField(exist = false)
    private String sellerNickName;

    @TableField(exist = false)
    private String sellerIcon;
}
