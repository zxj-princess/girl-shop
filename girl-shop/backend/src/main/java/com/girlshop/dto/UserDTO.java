package com.girlshop.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    /**
     * 手机号（个人中心展示用）
     */
    private String phone;
}
