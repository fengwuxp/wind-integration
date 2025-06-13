package com.wind.integration.core.constant;

/**
 * 常用的字段验证常量
 *
 * @author wuxp
 * @date 2025-06-13 11:23
 **/
public final class CommonlyFieldValidationConstants {

    private CommonlyFieldValidationConstants() {
        throw new AssertionError();
    }

    public static final String PASSWORD_REGEXP =
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#\\$%\\^&\\*\\(\\)\\-\\+=])[A-Za-z\\d!@#\\$%\\^&\\*\\(\\)\\-\\+=]{8,32}$";


    public static final String PASSWORD_MESSAGE = "密码必须包含四种及以上类型：大写字母、小写字母、数字、特殊字符（!@#$%^&*()-+=），并且长度在 8~32 位之间";
}
