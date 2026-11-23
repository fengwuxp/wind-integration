package com.wind.integration.alibaba.credentials;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 产品连接配置和可选的独立认证配置；仅设置端点不构成身份覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AlibabaCloudProductProperties extends AlibabaCloudCredentialsProperties {

    /**
     * 具体云产品的访问端点，不参与身份来源选择。
     */
    @NotBlank
    private String endpoint;
}
