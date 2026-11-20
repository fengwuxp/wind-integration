package com.wind.integration.alibaba.credentials;

/**
 * 阿里云产品配置约定，公共凭据模块不负责 Spring 绑定或产品客户端创建。
 */
public final class AlibabaCloudProducts {

    /**
     * Spring 应用中的阿里云统一配置前缀。
     */
    public static final String CONFIG_PREFIX = "wind.alibaba.cloud";

    /**
     * 对象存储产品标识。
     */
    public static final String OSS = "oss";

    /**
     * 邮件推送产品标识。
     */
    public static final String DM = "dm";

    private AlibabaCloudProducts() {
        throw new AssertionError();
    }
}
