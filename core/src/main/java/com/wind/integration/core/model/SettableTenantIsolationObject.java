package com.wind.integration.core.model;

import java.io.Serializable;

/**
 * 可设置租户归属的对象。
 * 在 {@link TenantIsolationObject} 的读取契约上增加赋值能力，
 * 供实体插入填充等场景使用；租户来源与赋值时机由调用方决定。
 *
 * @param <I> 对象 ID 与租户 ID 的类型
 * @author wuxp
 * @since 2026-11-05
 */
public interface SettableTenantIsolationObject<I extends Serializable> extends TenantIsolationObject<I> {

    /**
     * 设置对象所属的租户。
     *
     * @param tenantId 租户 ID，null 表示未绑定
     */
    void setTenantId(I tenantId);
}
