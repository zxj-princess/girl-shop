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
 * 关注表：买家关注卖家
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_follow")
public class Follow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关注者id（买家）
     */
    private Long userId;

    /**
     * 被关注者id（卖家）
     */
    private Long followUserId;

    private LocalDateTime createTime;

    // ---------- 展示附加字段（非数据库字段） ----------
    @TableField(exist = false)
    private String followNickName;

    @TableField(exist = false)
    private String followIcon;

    @TableField(exist = false)
    private Integer productCount;

    /** 粉丝昵称（粉丝列表展示用） */
    @TableField(exist = false)
    private String fanNickName;

    /** 粉丝头像（粉丝列表展示用） */
    @TableField(exist = false)
    private String fanIcon;
}
