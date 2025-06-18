package com.wind.integration.oss.alibaba;

import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.internal.OSSUtils;
import com.wind.common.WindConstants;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
     * 获取 bucket 公网 endpoint
     *
     * @param bucketName   bucket 名称
     * @param endpointHost endpoint host  (不带协议)
     * @return bucket 公网 endpoint
     */
    public static String getBucketEndpoint(String bucketName, String endpointHost) {
        String result;
        if (endpointHost.contains(INTERNAL_FLAG)) {
            // 替换掉 -internal
            result = bucketName + WindConstants.DOT + endpointHost.replaceAll(INTERNAL_FLAG.substring(0, INTERNAL_FLAG.length() - 1), "");
        } else {
            result = bucketName + WindConstants.DOT + endpointHost;
        }
        return OSSUtils.toEndpointURI(result, "https").toString();
    }

    /**
     * 获取 bucket 内网 endpoint
     *
     * @param bucketName   bucket 名称
     * @param endpointHost endpoint host (不带协议)
     * @return bucket 内网 endpoint
     */
    public static String getBucketInternalEndpoint(String bucketName, String endpointHost) {
        String result;
        if (endpointHost.contains(INTERNAL_FLAG)) {
            result = bucketName + WindConstants.DOT + endpointHost;
        } else {
            // 拼接上 -internal
            endpointHost = endpointHost.substring(0, endpointHost.length() - ALIYUN_DOMAIN.length() - 1) + INTERNAL_FLAG + ALIYUN_DOMAIN;
            result = bucketName + WindConstants.DOT + endpointHost;
        }
        return OSSUtils.toEndpointURI(result, "https").toString();
    }
}
