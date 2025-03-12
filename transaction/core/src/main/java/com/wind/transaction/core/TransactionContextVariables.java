package com.wind.transaction.core;

import com.wind.common.exception.AssertUtils;
import com.wind.core.WritableContextVariables;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 交易上下文变量
 *
 * @author wuxp
 * @date 2023-12-18 11:34
 **/
public final class TransactionContextVariables implements WritableContextVariables {

    /**
     * 上下文变量
     */
    private final Map<String, Object> variables;

    private TransactionContextVariables(@NotNull Map<String, Object> variables) {
        AssertUtils.notNull(variables, "argument variables must not null");
        this.variables = variables;
    }

    public static TransactionContextVariables of() {
        return new TransactionContextVariables(new HashMap<>());
    }

    /**
     * 通过浅拷贝的方式创建一个新的实例
     *
     * @param variables 变量
     * @return TransactionContextVariables 实例
     */
    public static TransactionContextVariables of(@NotNull Map<String, Object> variables) {
        return new TransactionContextVariables(new HashMap<>(variables));
    }

    @Override
    public WritableContextVariables putVariable(@NotBlank String name, Object val) {
        variables.put(name, val);
        return this;
    }

    @Override
    public WritableContextVariables removeVariable(@NotBlank String name) {
        variables.remove(name);
        return this;
    }

    @Override
    public Map<String, Object> getContextVariables() {
        return Collections.unmodifiableMap(variables);
    }
}
