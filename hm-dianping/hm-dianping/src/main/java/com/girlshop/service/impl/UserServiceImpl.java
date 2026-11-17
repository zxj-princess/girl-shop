package com.girlshop.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.LoginFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.dto.UserDTO;
import com.girlshop.entity.User;
import com.girlshop.mapper.UserMapper;
import com.girlshop.service.IUserService;
import com.girlshop.utils.RegexUtils;
import com.girlshop.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.girlshop.utils.RedisConstants.*;
import static com.girlshop.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource private com.girlshop.realtime.RealtimeHub realtime;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @org.springframework.beans.factory.annotation.Value("${shop.auth.log-simulated-code:true}") private boolean logSimulatedCode;

    @Override
    public Result sendCode(String phone) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2. 生成 6 位验证码
        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent("login:rate:" + phone, "1", 60, TimeUnit.SECONDS)))
            return Result.fail("验证码发送过于频繁，请稍后重试");
        String code = RandomUtil.randomNumbers(6);
        // 3. 保存验证码到 redis，2 分钟有效
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 模拟发送短信（真实环境对接短信服务商）
        if(logSimulatedCode)log.info("本地模拟短信验证码：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        if (loginForm == null || loginForm.getPhone() == null || loginForm.getCode() == null)
            return Result.fail("请填写手机号和验证码");
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2. 校验验证码
        org.springframework.data.redis.core.script.DefaultRedisScript<Long> script = new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "local n=redis.call('incr',KEYS[2]); if n==1 then redis.call('expire',KEYS[2],120) end; "
                + "if n>5 then return 0 end; if redis.call('get',KEYS[1])==ARGV[1] then redis.call('del',KEYS[1]); return 1 end; return 0", Long.class);
        Long verified = stringRedisTemplate.execute(script, java.util.Arrays.asList(LOGIN_CODE_KEY + phone, "login:attempt:" + phone), loginForm.getCode());
        if (!Long.valueOf(1).equals(verified)) {
            return Result.fail("验证码错误或已过期！");
        }
        // 3. 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 4. 不存在则注册新用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 5. 保存用户到 redis（token 作为 key）
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 6. 返回 token
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        realtime.revoke(token);
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @Override
    public Result updateNickName(String token, String nickName) {
        if (nickName == null || nickName.trim().isEmpty()) {
            return Result.fail("昵称不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        User user = getById(userId);
        user.setNickName(nickName.trim());
        updateById(user);
        // 同步刷新 Redis 登录缓存中的昵称，保证后续请求拿到最新信息
        if (token != null && !token.isEmpty()) {
            stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, "nickName", nickName.trim());
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(8));
        save(user);
        return user;
    }
}
