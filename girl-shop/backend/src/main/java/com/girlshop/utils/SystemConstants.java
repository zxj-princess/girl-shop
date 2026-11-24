package com.girlshop.utils;

public class SystemConstants {
    // 图片上传目录：位于 nginx html 下，由 nginx 以 /imgs/ 前缀对外提供访问
    public static final String IMAGE_UPLOAD_DIR = "D:\\shixi\\girl shop\\nginx-1.18.0\\nginx-1.18.0\\html\\girl-shop\\imgs";
    // 图片对外访问前缀
    public static final String IMAGE_URL_PREFIX = "/imgs/";
    public static final String USER_NICK_NAME_PREFIX = "girl_";
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
}