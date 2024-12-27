package com.wind.integration.oss.alibaba;

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
     * oss endpoint (地域节点)
     */
    private String endpoint;

    /**
     * 上传内容限制 200MB
     */
    private Long maxContentLength = 200 * 1024 * 1024L;


}
