package com.wind.integration.core.context;

import com.wind.integration.core.enums.DataIsolationDimensions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 当前上下文临时忽略的隔离维度；回调退出时恢复原忽略状态，不自动传播到异步线程。
 * 读取范围由规则的供应者提供；手动忽略不删除业务 SQL 已有条件。
 *
 * @author wuxp
 * @date 2026-10-20 15:30
 **/
@NullMarked
public final class DataIsolationQueryIgnoreHolder {

    private static final ThreadLocal<@Nullable Set<DataIsolationDimensions>> IGNORED_DIMENSIONS = new ThreadLocal<>();

    private DataIsolationQueryIgnoreHolder() {
        throw new AssertionError();
    }

    public static boolean isIgnored(DataIsolationDimensions dimension) {
        return current().contains(dimension);
    }

    /**
     * 执行期间忽略指定维度，与外层已忽略维度合并；正常或异常退出后恢复原忽略状态。
     *
     * @param action     执行逻辑
     * @param dimensions 待忽略的一个或多个维度；空参数不新增忽略项，重复维度按一个处理
     */
    public static void runIgnoring(Runnable action, DataIsolationDimensions... dimensions) {
        Set<DataIsolationDimensions> previous = enterIgnoring(dimensions);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    /**
     * 在忽略指定维度的作用域内返回执行结果，退出时恢复原忽略状态。
     *
     * @param action     执行逻辑
     * @param dimensions 待忽略的一个或多个维度；与外层合并，空参数不新增忽略项
     * @param <T>        返回值类型
     * @return 执行结果
     * @throws Exception 执行逻辑抛出的异常
     */
    public static <T> T callIgnoring(Callable<T> action, DataIsolationDimensions... dimensions) throws Exception {
        Set<DataIsolationDimensions> previous = enterIgnoring(dimensions);
        try {
            return action.call();
        } finally {
            restore(previous);
        }
    }

    public static void clear() {
        IGNORED_DIMENSIONS.remove();
    }

    private static Set<DataIsolationDimensions> enterIgnoring(DataIsolationDimensions[] dimensions) {
        Set<DataIsolationDimensions> previous = current();
        if (dimensions.length == 0) {
            return previous;
        }
        Set<DataIsolationDimensions> ignored = EnumSet.noneOf(DataIsolationDimensions.class);
        ignored.addAll(previous);
        Collections.addAll(ignored, dimensions);
        IGNORED_DIMENSIONS.set(ignored);
        return previous;
    }

    private static Set<DataIsolationDimensions> current() {
        Set<DataIsolationDimensions> dimensions = IGNORED_DIMENSIONS.get();
        return dimensions == null ? Set.of() : dimensions;
    }

    private static void restore(Set<DataIsolationDimensions> dimensions) {
        if (dimensions.isEmpty()) {
            IGNORED_DIMENSIONS.remove();
        } else {
            IGNORED_DIMENSIONS.set(dimensions);
        }
    }
}
