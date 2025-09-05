package com.wind.transaction.core;

import com.wind.common.enums.DescriptiveEnum;
import com.wind.common.exception.AssertUtils;
import com.wind.core.WritableContextVariables;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 交易账户标识
 *
 * @author wuxp
 * @date 2024-05-14 16:26
 **/
@EqualsAndHashCode
@ToString
public class TransactionAccountId implements WritableContextVariables {

    /**
     * 账号唯一标识
     */
    @NotNull
    private final String id;

    /**
     * 账号类型
     */
    @NotNull
    private final DescriptiveEnum type;

    /**
     * 归属的用户
     */
    @NotNull
    private final Object userId;

    /**
     * 上下文变量
     */
    @NotNull
    private final Map<String, Object> variables = new HashMap<>();

    private TransactionAccountId(@NotNull String id, @NotNull DescriptiveEnum type, @NotNull Object userId) {
        AssertUtils.hasText(id, "argument id must not empty");
        AssertUtils.notNull(type, "argument type must not null");
        AssertUtils.notNull(userId, "argument userId must not null");
        this.id = id;
        this.type = type;
        this.userId = userId;
    }

    public static TransactionAccountId of(String id, DescriptiveEnum type, Object userId) {
        return new TransactionAccountId(id, type, userId);
    }

    @Override
    public TransactionAccountId putVariable(String name, @Nullable Object val) {
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

    @NotNull
    public String getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public <T> T getType() {
        return (T) type;
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public <T> T getUserId() {
        return (T) userId;
    }

}
