package com.wind.integration.core.context;

import com.wind.common.exception.AssertUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;

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

    /**
     * 设置租户 ID
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(@NonNull Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 运行时指定租户 ID
     *
     * @param tenantId 租户 ID
     * @param action   执行方法
     */
    public static void runWithTenantId(@NonNull Long tenantId, @NonNull Runnable action) {
        setTenantId(tenantId);
        action.run();
    }

    /**
     * 运行时指定租户 ID
     *
     * @param tenantId 租户 ID
     * @param action   执行方法
     * @param <T>      返回值类型
     * @return 返回值
     */
    public static <T> T callWithTenantId(@NonNull Long tenantId, @NonNull Callable<T> action) throws Exception {
        setTenantId(tenantId);
        return action.call();
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
