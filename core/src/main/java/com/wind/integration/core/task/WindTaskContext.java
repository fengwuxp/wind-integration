package com.wind.integration.core.task;

import com.wind.common.exception.AssertUtils;
import com.wind.core.ReadonlyContextVariables;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * 任务上下文
 *
 * @author wuxp
 * @date 2026-01-09 12:02
 **/
public interface WindTaskContext extends ReadonlyContextVariables {

    @NotNull
    static WindTaskContext empty() {
        return of(Collections.emptyMap());
    }

    @NotNull
    static WindTaskContext of(@NotNull Map<String, Object> contextVariables) {
        AssertUtils.notNull(contextVariables, "argument contextVariables must not null");
        return () -> contextVariables;
    }
}
