package com.wind.integration.kms.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 凭据详情
 *
 * @author wuxp
 * @date 2025-02-17 18:12
 **/
@Data
public class KmsSecretDetailsDTO {

    /**
     * 凭据名称
     */
    @NotBlank
    private String secretName;

    /**
     * 凭据版本
     */
    private String version;

    /**
     * 凭据内容
     */
    @NotBlank
    private String content;

    /**
     * 原始数据
     */
    @NotNull
    private Object raw;
}
