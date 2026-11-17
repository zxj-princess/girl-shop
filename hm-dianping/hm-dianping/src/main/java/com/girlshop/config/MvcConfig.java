package com.girlshop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.girlshop.utils.LoginInterceptor;
import com.girlshop.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.List;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 扩展 HTTP 消息转换器：将所有 Long 类型序列化为 String
     * 原因：雪花算法生成的 ID 是 17+ 位，超过 JavaScript Number 精度（2^53 ≈ 9e15），
     * 前端 JSON.parse 后会丢失精度，导致 /order/{id}/pay 等接口找不到订单
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                MappingJackson2HttpMessageConverter jackson = (MappingJackson2HttpMessageConverter) converter;
                ObjectMapper mapper = jackson.getObjectMapper();
                SimpleModule module = new SimpleModule();
                // Long → String，避免前端精度丢失
                module.addSerializer(Long.class, ToStringSerializer.instance);
                module.addSerializer(Long.TYPE, ToStringSerializer.instance);
                mapper.registerModule(module);
            }
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器：除公开接口外均需登录
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/ws/messages",
                        "/user/login",
                        "/category/list",
                        "/product/list",
                        "/product/detail/**",
                        "/product/flash",
                        "/seller/*",
                        "/seller/*/products",
                        "/message/list/**",
                        "/review/list/**"
                ).order(1);
        // token 刷新拦截器：刷新并填充 ThreadLocal
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .excludePathPatterns("/user/code", "/user/login")
                .order(0);
    }
}
