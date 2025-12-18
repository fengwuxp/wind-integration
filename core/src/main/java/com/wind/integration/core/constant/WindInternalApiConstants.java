package com.wind.integration.core.constant;

/**
 * 内网接口常量
 * 参见：https://www.yuque.com/suiyuerufeng-akjad/wind/lynoufptdg3ml6bs
 *
 * @author wuxp
 * @note 内网接口不许暴露到公网
 * @date 2025-12-18 13:21
 **/
public final class WindInternalApiConstants {

    private WindInternalApiConstants() {
        throw new AssertionError();
    }

    /**
     * 内网接口前缀
     */
    public static final String INC_API_PREFIX = "/internal/v1";

    /**
     * 内网开放 API 前缀 （不需要验签）
     */
    public static final String INC_BASIC_API_PREFIX = INC_API_PREFIX + "/basic/";

    /**
     * 内网安全 API 前缀 （需要验签）
     */
    public static final String INC_SECURE_API_PREFIX = INC_API_PREFIX + "/secure/";
}
