package com.wind.integration.core.context;

import com.wind.common.exception.AssertUtils;
import com.wind.integration.core.enums.DataIsolationDimensions;
import com.wind.trace.WindTraceContext;
import com.wind.trace.WindTracer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 设置当前上下文的隔离维度；回调退出时恢复原忽略状态，不自动传播到异步线程。
 *
 * @author wuxp
 * @since 2026-10-16
 */
@NullMarked
public final class DataIsolationContextHolder {

    private DataIsolationContextHolder() {
        throw new AssertionError();
    }


    /**
     * 运行时指定租户 ID，退出时恢复原租户 ID
     *
     * @param tenantId 租户 ID
     * @param action   执行方法
     */
    public static void runWithTenant(Serializable tenantId, Runnable action) {
        runWith(Map.of(DataIsolationDimensions.TENANT, tenantId), action);
    }

    /**
     * 运行时指定租户 ID，退出时恢复原租户 ID
     *
     * @param tenantId 租户 ID
     * @param action   执行方法
     * @param <T>      返回值类型
     * @return 返回值
     */
    public static <T> T callWithTenantId(Serializable tenantId, Callable<T> action) throws Exception {
        return callWith(Map.of(DataIsolationDimensions.TENANT, tenantId), action);
    }

    /**
     * 运行时指定 Workspace ID，退出时恢复原 Workspace
     *
     * @param workspaceId 工作空间 ID
     * @param action      执行方法
     */
    public static void runWithWorkspace(Serializable workspaceId, Runnable action) {
        runWith(Map.of(DataIsolationDimensions.WORKSPACE, workspaceId), action);
    }

    /**
     * 运行时指定 Workspace ID，退出时恢复原 Workspace
     *
     * @param workspaceId 工作空间 ID
     * @param action      执行方法
     * @return 返回值
     */
    public static <T> T callWithWorkspace(Serializable workspaceId, Callable<T> action) throws Exception {
        return callWith(Map.of(DataIsolationDimensions.WORKSPACE, workspaceId), action);
    }

    /**
     * 基于隔离上下文
     *
     * @param values 隔离的上下文执行
     * @param action 执行的函数
     */
    public static void runWith(Map<DataIsolationDimensions, Object> values, Runnable action) {
        Long prevTenantId = TenantContextHolder.getTenantId();
        Object tenantId = values.get(DataIsolationDimensions.TENANT);
        if (tenantId != null) {
            // TODO 待退役
            TenantContextHolder.setTenantId((Long) tenantId);
        }
        try {
            WindTracer.TRACER.run(() -> {
                values.forEach((dimension, value) -> WindTracer.TRACER.putVariable(dimension.getTraceAttributeName(), value));
                action.run();
            });
        } finally {
            if (prevTenantId == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setTenantId(prevTenantId);
            }
        }
    }

    /**
     * 基于隔离上下文
     *
     * @param values 隔离的上下文执行
     * @param action 执行的函数
     */
    public static <V> V callWith(Map<DataIsolationDimensions, Object> values, Callable<V> action) {
        Object tenantId = values.get(DataIsolationDimensions.TENANT);
        Long prevTenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            TenantContextHolder.setTenantId((Long) tenantId);
        }
        try {
            return WindTracer.TRACER.call(() -> {
                values.forEach((dimension, value) -> WindTracer.TRACER.putVariable(dimension.getTraceAttributeName(), value));
                return action.call();
            });
        } finally {
            if (prevTenantId == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setTenantId(prevTenantId);
            }
        }
    }

    @Nullable
    public static <T> T getTenantId() {
        Serializable result = getDataIsolationDimensioId(DataIsolationDimensions.TENANT);
        // 兼容逻辑
        return result == null ? (T) TenantContextHolder.getTenantId() : (T) result;
    }

    public static <T> T requireTenantId() {
        return Objects.requireNonNull(getTenantId(), "context workspaceId is must not null");
    }

    @Nullable
    public static <T> T getWorkspaceId() {
        return getDataIsolationDimensioId(DataIsolationDimensions.WORKSPACE);
    }

    public static <T> T requireWorkspaceId() {
        return Objects.requireNonNull(getWorkspaceId(), "context workspaceId is must not null");
    }

    @Nullable
    public static String getEnv() {
        return getDataIsolationDimensioId(DataIsolationDimensions.ENVIRONMENT);
    }

    public static String requireEnv() {
        String env = getEnv();
        AssertUtils.hasText(env, "context env is must not empty");
        return env;
    }

    public static boolean isTestData() {
        return Boolean.TRUE.equals(getDataIsolationDimensioId(DataIsolationDimensions.TEST_DATA));
    }

    @Nullable
    public static <T> T getDataIsolationDimensioId(DataIsolationDimensions dimensions) {
        WindTraceContext context = WindTracer.TRACER.currentContext().orElseGet(WindTraceContext::root);
        return context.getContextVariable(dimensions.getTraceAttributeName());
    }
}
