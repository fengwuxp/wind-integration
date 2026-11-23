package com.wind.integration.kms.model.request;

import lombok.Data;

/**
 * @author wuxp
 * @date 2025-02-17 18:04
 **/
@Data
public abstract class KmsCryptoReqeustOptions {

    /**
     * 加密算法
     */
    private String algorithm;

    /**
     * 填充模式
     */
    private String paddingMode;
}
