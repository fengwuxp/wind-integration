package com.wind.integration.core.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * 业务任务定义
 *
 * @param id               任务 id，如果为空则表示需要新建任务
 * @param name             任务名称，为空则采用 {@link  #scene} 自动生成
 * @param scene            业务场景
 * @param businessIds      任务业务 ids
 * @param contextVariables 上下文变量
 * @param retriesCount     已执行次数
 * @param maxRetries       允许执行的最大次数
 * @param parentId         父任务 ID
 * @param cron             重试间隔 cron 表达式
 * @param source           任务来源
 * @param sourceId         任务来源标识
 * @author wuxp
 * @date 2024-10-08 12:57
 */
@Builder
public record WindTaskDefinition(@Nullable Long id, @Nullable String name, @NotNull String scene, @NotEmpty Set<String> businessIds,
                                 @NotNull Map<String, Object> contextVariables, Integer retriesCount, Integer maxRetries, Long parentId, @NotBlank String cron,
                                 @NotNull String source, @NotNull String sourceId) {

    /**
     * 任务是否可以执行
     *
     * @return if true 允许执行
     */
    public boolean isRetryable() {
        if (retriesCount == null || maxRetries == null) {
            return false;
        }
        return maxRetries > retriesCount;
    }

    /**
     * 增加执行次数
     *
     * @return 新任务定义
     */
    public WindTaskDefinition increaseRetriesCount() {
        return WindTaskDefinition.builder()
                .id(id)
                .name(name)
                .businessIds(businessIds)
                .scene(scene)
                .contextVariables(contextVariables)
                .cron(cron)
                .parentId(parentId)
                .source(source)
                .sourceId(sourceId)
                .maxRetries(maxRetries)
                .retriesCount(retriesCount == null ? 1 : retriesCount + 1)
                .build();
    }
}
