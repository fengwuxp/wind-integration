package com.wind.integration.kms;

import com.wind.integration.kms.model.dto.KmsSecretDetailsDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;

/**
 * 凭据管理 Client API 抽象，用于适配不同的凭据托管实现
 *
 * @author wuxp
 * @date 2025-02-17 18:11
 **/
public interface WindCredentialsClient {

    /**
     * 获取凭据
     *
     * @param credentialsName 凭据名称
     * @return 凭据内容
     */
    default String getCredentialsAsText(@NotBlank String credentialsName) {
        return getCredentials(credentialsName).getContent();
    }

    /**
     * 获取凭据
     *
     * @param credentialsName 凭据名称
     * @return 凭据详情
     */
    default KmsSecretDetailsDTO getCredentials(@NotBlank String credentialsName) {
        return getCredentials(credentialsName, null);
    }

    /**
     * 获取凭据
     *
     * @param credentialsName 凭据名称
     * @param version         凭据版本标识
     * @return 凭据详情
     */
    KmsSecretDetailsDTO getCredentials(@NotBlank String credentialsName, @Null String version);
}
