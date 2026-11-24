package com.girlshop.utils;

public class RedisConstants {
    // 登录验证码 & token
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    // 商品缓存（解决穿透：空值缓存；解决击穿：逻辑过期+互斥锁重建）
    public static final Long CACHE_NULL_TTL = 2L;
    public static final Long CACHE_PRODUCT_TTL = 30L;
    public static final String CACHE_PRODUCT_KEY = "cache:product:v2:";
    public static final String CACHE_FLASH_PRODUCT_KEY = "cache:flash-product:v2:";
    public static final Long LOCK_PRODUCT_TTL = 10L;
    public static final String LOCK_PRODUCT_KEY = "lock:product:";

    // 捡漏秒杀
    public static final String FLASH_STOCK_KEY = "flash:stock:";
    public static final String FLASH_ORDER_KEY = "flash:order:";
    public static final String FLASH_LIST_KEY = "flash:list";
}
