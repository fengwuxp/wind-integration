package com.wind.integration.core.task;

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
    static WindTaskContext of(Map<String, Object> contextVariables) {
        Map<String, Object> variables = contextVariables == null ? Collections.emptyMap() : contextVariables;
        return () -> variables;
    }
}
