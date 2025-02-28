package com.wind.integration.oss.alibaba;

import com.aliyun.oss.ClientConfiguration;
import com.wind.common.WindConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 阿里云 oss 配置
 *
 * @author wuxp
 * @date 2024-12-25 15:41
 **/
@Data
public class AlibabaCloudOssProperties {

    private static final String ALIYUN_DOMAIN = "aliyuncs.com";

    private static final String INTERNAL_FLAG = "-internal.";

    /**
     * 是否启用
     */
    @NotNull
    private Boolean enabled = true;

    /**
     * Access key就像用户ID，可以唯一标识你的账户
     */
    @NotBlank
    private String accessKey;

    /**
     * Secret key是你账户的密码
     */
    @NotBlank
    private String secretKey;

    /**
     * oss endpoint
     */
    @NotBlank
    private String endpoint;

    public ClientConfiguration getDefaultClientConfiguration() {
        ClientConfiguration result = new ClientConfiguration();
        result.setMaxConnections(256);
        return result;
    }

    /**
     * 获取 bucket 公网域名
     *
     * @param bucketName   bucket 名称
     * @param endpointHost endpoint host  (不带协议)
     * @return bucket 公网域名
     */
    public static String getBucketDomain(String bucketName, String endpointHost) {
        if (endpointHost.contains(INTERNAL_FLAG)) {
            // 替换掉 -internal
            return bucketName + WindConstants.DOT + endpointHost.replaceAll(INTERNAL_FLAG.substring(0, INTERNAL_FLAG.length() - 1), "");
        }
        return bucketName + WindConstants.DOT + endpointHost;
    }

    /**
     * 获取 bucket 内网域名
     *
     * @param bucketName   bucket 名称
     * @param endpointHost endpoint host (不带协议)
     * @return bucket 内网域名
     */
    public static String getBucketInternalDomain(String bucketName, String endpointHost) {
        if (endpointHost.contains(INTERNAL_FLAG)) {
            return bucketName + WindConstants.DOT + endpointHost;
        }
        // 拼接上 -internal
        endpointHost = endpointHost.substring(0, endpointHost.length() - ALIYUN_DOMAIN.length() - 1) + INTERNAL_FLAG + ALIYUN_DOMAIN;
        return bucketName + WindConstants.DOT + endpointHost;
    }
}
