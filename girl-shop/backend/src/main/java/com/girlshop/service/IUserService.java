package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.LoginFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.User;

/**
 * 用户服务
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);

    Result logout(String token);

    /**
     * 修改昵称，并同步刷新 Redis 登录缓存
     */
    Result updateNickName(String token, String nickName);
}