package com.wind.integration.core.context;

import com.wind.common.exception.AssertUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 基于上下文的租户 ID 持有者
 *
 * @author wuxp
 * @date 2023-12-28 16:23
 **/
public final class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContextHolder() {
        throw new AssertionError();
    }

    public static void setTenantId(@NonNull Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    @Nullable
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    @NonNull
    public static Long requireTenantId() {
        Long tenantId = getTenantId();
        AssertUtils.notNull(tenantId, "context tenantId must not be null");
        return tenantId;
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
