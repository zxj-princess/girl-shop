package com.girlshop.controller;

import cn.hutool.core.bean.BeanUtil;
import com.girlshop.dto.LoginFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.dto.UserDTO;
import com.girlshop.entity.User;
import com.girlshop.entity.UserInfo;
import com.girlshop.service.IUserInfoService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /**
     * 手机号验证码登录/注册
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        if (StringUtils.hasText(token)) {
            userService.logout(token);
        }
        return Result.ok();
    }

    /**
     * 修改昵称
     */
    @PutMapping("/nickname")
    public Result updateNickName(@RequestParam("nickName") String nickName, HttpServletRequest request) {
        return userService.updateNickName(request.getHeader("authorization"), nickName);
    }

    /**
     * 当前登录用户（从数据库查最新信息，保证昵称/头像/手机号实时准确）
     */
    @GetMapping("/me")
    public Result me() {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        User user = userService.getById(userDTO.getId());
        if (user == null) {
            return Result.ok(userDTO);
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    /**
     * 查询用户详情
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    /**
     * 查询/保存个人资料
     */
    @GetMapping("/info")
    public Result info() {
        Long userId = UserHolder.getUser().getId();
        UserInfo info = userInfoService.getById(userId);
        return Result.ok(info);
    }

    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserInfo userInfo) {
        Long userId = UserHolder.getUser().getId();
        userInfo.setUserId(userId);
        boolean exist = userInfoService.getById(userId) != null;
        userInfo.setCreateTime(null);
        userInfo.setUpdateTime(null);
        if (exist) {
            userInfoService.updateById(userInfo);
        } else {
            userInfoService.save(userInfo);
        }
        return Result.ok();
    }
}