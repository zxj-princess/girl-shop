package com.girlshop.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则校验工具类
 */
public class RegexUtils {

    private static final Pattern PHONE_PATTERN = Pattern.compile(RegexPatterns.PHONE_REGEX);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(RegexPatterns.EMAIL_REGEX);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(RegexPatterns.PASSWORD_REGEX);
    private static final Pattern VERIFY_CODE_PATTERN = Pattern.compile(RegexPatterns.VERIFY_CODE_REGEX);

    /**
     * 手机号是否无效
     */
    public static boolean isPhoneInvalid(String phone) {
        return !PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 邮箱是否无效
     */
    public static boolean isEmailInvalid(String email) {
        return !EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 密码是否无效
     */
    public static boolean isPasswordInvalid(String password) {
        return !PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * 验证码是否无效
     */
    public static boolean isCodeInvalid(String code) {
        return !VERIFY_CODE_PATTERN.matcher(code).matches();
    }
}
