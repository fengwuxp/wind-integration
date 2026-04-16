package com.wind.integration.core.task;

import org.jspecify.annotations.NonNull;

import java.time.LocalDateTime;

/**
 * 支持重试的任务定义
 *
 * @author wuxp
 * @date 2026-04-15 15:37
 **/
public interface WindRetryableTask {

    /**
     * 全局唯一 ID（用于幂等）
     */
    @NonNull
    String getRetryId();

    /**
     * 当前重试次数，从 0 开始
     */
    int getRetryCount();

    /**
     * 最大重试次数
     */
    int getMaxRetryCount();

    /**
     * 下次可重试时间
     */
    LocalDateTime getNextRetryTime();

    /**
     * 是否允许重试
     */
    boolean canRetry();
}
