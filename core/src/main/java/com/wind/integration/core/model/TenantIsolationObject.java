package com.wind.integration.core.model;

import org.jspecify.annotations.NonNull;

import java.io.Serializable;

/**
 * 支持租户隔离的对象
 *
 * @param <I> id 类型
 * @author wuxp
 * @date 2024-12-15 19:04
 **/
public interface TenantIsolationObject<I extends Serializable> extends IdObject<I> {

    /**
     * @return 获取租户 ID
     */
    @NonNull
    I getTenantId();

}
