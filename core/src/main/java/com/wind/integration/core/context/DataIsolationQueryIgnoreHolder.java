package com.wind.integration.core.context;

import com.wind.integration.core.enums.DataIsolationDimensions;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 当前调用作用域临时忽略的隔离维度；回调退出时自动恢复，不自动传播到其他线程。
 * 读取范围由规则的供应者提供；手动忽略不删除业务 SQL 已有条件。
 *
 * @author wuxp
 * @date 2026-10-20 15:30
 **/
@NullMarked
public final class DataIsolationQueryIgnoreHolder {

    private static final ScopedValue<IgnoreScope> IGNORED_DIMENSIONS = ScopedValue.newInstance();

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
        if (dimensions.length == 0) {
            action.run();
            return;
        }
        ScopedValue.where(IGNORED_DIMENSIONS, ignoring(dimensions)).run(action);
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
        if (dimensions.length == 0) {
            return action.call();
        }
        return ScopedValue.where(IGNORED_DIMENSIONS, ignoring(dimensions)).call(action::call);
    }

    private static IgnoreScope ignoring(DataIsolationDimensions[] dimensions) {
        Set<DataIsolationDimensions> ignored = EnumSet.noneOf(DataIsolationDimensions.class);
        ignored.addAll(current());
        Collections.addAll(ignored, dimensions);
        return new IgnoreScope(Thread.currentThread(), Set.copyOf(ignored));
    }

    private static Set<DataIsolationDimensions> current() {
        if (!IGNORED_DIMENSIONS.isBound()) {
            return Set.of();
        }
        IgnoreScope scope = IGNORED_DIMENSIONS.get();
        if (scope.owner() != Thread.currentThread()) {
            return Set.of();
        }
        return scope.dimensions();
    }

    /**
     * 结构化子任务会继承 ScopedValue 绑定，忽略权限仍仅供创建作用域的线程使用。
     */
    private record IgnoreScope(Thread owner, Set<DataIsolationDimensions> dimensions) {
    }
}
