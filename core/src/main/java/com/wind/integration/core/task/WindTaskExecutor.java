package com.wind.integration.core.task;

import org.jspecify.annotations.NonNull;

/**
 * 任务执行器，参见：https://captekefu.yuque.com/mspq9a/nobe-v2/wlxxp9g5d9za2lz7
 *
 * @author wuxp
 * @date 2024-10-08 13:00
 **/
public interface WindTaskExecutor {

    /**
     * 执行任务
     *
     * @param taskDefinition 任务定义
     * @return 任务 ID
     */
    @NonNull
    Long execute(@NonNull WindTaskDefinition taskDefinition);
}
