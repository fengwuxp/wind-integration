package com.wind.integration.kms.model.dto;

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
    private String secretName;

    /**
     * 凭据版本
     */
    private String version;

    /**
     * 凭据内容
     */
    private String content;

    /**
     * 原始数据
     */
    private Object raw;
}
