package com.wind.integration.core.task;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * 通用任务 Runnable，支持幂等和重试处理，支持串行处理 {@link #isSerial()}
 *
 * @author wuxp
 * @date 2024-10-08 14:31
 **/
@NullMarked
public interface WindTaskRunnable {

    /**
     * 执行任务
     *
     * @param businessId 业务 Id
     * @param context    任务上下文
     */
    void run(String businessId, WindTaskContext context);

    /**
     * 任务路由场景标识，用于任务分发与执行器路由，决定最终由哪个 {@link WindTaskRunnable} 执行
     * {@link WindTaskDefinition#scene()}
     */
    @NonNull
    String getScene();

    /**
     * 是否需要串行执行
     *
     * @return if true 是
     */
    default boolean isSerial() {
        return true;
    }

    /**
     * 预计处理时长，一般和  {@link #isSerial()} 配合使用，用于估算任务执行时间，较为灵活的控制锁的获取和释放
     *
     * @return 毫秒数
     */
    default int getEstimatedExecuteTime() {
        return -1;
    }

    /**
     * 每秒执行的任务数
     *
     * @return 每秒执行的任务数
     */
    default int getTokenPerSecond() {
        return 15;
    }
}
