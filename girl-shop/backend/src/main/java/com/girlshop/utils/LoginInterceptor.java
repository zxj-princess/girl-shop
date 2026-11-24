package com.girlshop.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.girlshop.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    public LoginInterceptor(){}
    public LoginInterceptor(org.springframework.jdbc.core.JdbcTemplate jdbc){this.jdbc=jdbc;}


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            //没有，需要拦截，设置状态码
            response.setStatus(401);
            //拦截
            return false;
        }
        if(jdbc!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM tb_account_restriction WHERE user_id=?",Integer.class,UserHolder.getUser().getId())>0){response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"success\":false,\"errorMsg\":\"账号已被限制，请联系管理员\"}");return false;}
        //有用户，则放行
        return true;
    }
}
