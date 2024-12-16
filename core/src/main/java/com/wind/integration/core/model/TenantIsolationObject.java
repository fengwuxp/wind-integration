package com.wind.integration.core.model;

/**
 * 支持租户隔离的对象
 *
 * @author wuxp
 * @date 2024-12-15 19:04
 **/
public interface TenantIsolationObject<ID> extends IdObject<ID> {

    /**
     * @return 获取租户 ID
     */
    ID getTenantId();

}
