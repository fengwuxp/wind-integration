package com.wind.integration.message.email.alibabacloud;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 阿里云邮件配置
 *
 * @author wuxp
 * @date 2025-12-11 16:48
 */
@Data
public class AlibabaCloudEmailProperties {

    /**
     * 阿里云 accessKey
     */
    @NotBlank
    private String accessKey;

    /**
     * 阿里云 secretKey
     */
    @NotBlank
    private String secretKey;

    /**
     * 阿里云 endpoint
     */
    @NotBlank
    private String endpoint;
}
