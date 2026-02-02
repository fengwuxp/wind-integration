package com.wind.transaction.core.account;

import com.wind.common.exception.AssertUtils;
import com.wind.core.WritableContextVariables;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 资金账户标识
 *
 * @author wuxp
 * @date 2024-05-14 16:26
 **/
@EqualsAndHashCode
@ToString
@Getter
public class FundsAccountId implements WritableContextVariables, Serializable {

    /**
     * 账号唯一标识
     */
    @NonNull
    private final String id;

    /**
     * 账户类型
     */
    @NonNull
    private final String accountType;

    /**
     * 上下文变量
     */
    @NonNull
    private final Map<String, Object> variables = new HashMap<>();

    private FundsAccountId(@NonNull String id, @NonNull String accountType) {
        AssertUtils.hasText(id, "argument id must not empty");
        AssertUtils.hasText(accountType, "argument accountType must not empty");
        this.id = id;
        this.accountType = accountType;
    }

    @NonNull
    public static FundsAccountId of(@NonNull String id, @NonNull String accountType) {
        return new FundsAccountId(id, accountType);
    }


    @Override
    public FundsAccountId putVariable(@NonNull String name, @Nullable Object val) {
        variables.put(name, val);
        return this;
    }

    @Override
    public WritableContextVariables removeVariable(@NonNull String name) {
        variables.remove(name);
        return this;
    }

    @Override
    public Map<String, Object> getContextVariables() {
        return Collections.unmodifiableMap(variables);
    }
}
