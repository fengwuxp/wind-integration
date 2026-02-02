package com.wind.transaction.core.account;

import com.wind.common.exception.AssertUtils;
import com.wind.core.WritableContextVariables;
import com.wind.transaction.core.enums.FundsAccountOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class FundsAccountId implements WritableContextVariables {

    /**
     * 账号唯一标识
     */
    @NonNull
    private final String id;

    /**
     * 主体唯一标识
     */
    @NonNull
    private final Serializable ownerId;

    /**
     * 账号类型
     */
    @NonNull
    private final FundsAccountOwnerType ownerType;

    /**
     * 上下文变量
     */
    @NonNull
    private final Map<String, Object> variables = new HashMap<>();

    private FundsAccountId(@NonNull String id, @NonNull Serializable ownerId, @NonNull FundsAccountOwnerType ownerType) {
        AssertUtils.hasText(id, "argument id must not empty");
        AssertUtils.notNull(ownerId, "argument ownerId must not empty");
        AssertUtils.notNull(ownerType, "argument ownerType must not null");
        this.id = id;
        this.ownerId = ownerId;
        this.ownerType = ownerType;
    }

    @NonNull
    public static FundsAccountId of(@NonNull String id, @NonNull Serializable ownerId, @NonNull FundsAccountOwnerType type) {
        return new FundsAccountId(id, ownerId, type);
    }

    @NonNull
    public static FundsAccountId user(@NonNull String id, @NonNull String uid) {
        return of(id, uid, FundsAccountOwnerType.USER);
    }

    @NonNull
    public static FundsAccountId merchant(@NonNull String id, @NonNull String merchantId) {
        return of(id, merchantId, FundsAccountOwnerType.MERCHANT);
    }


    @Override
    public FundsAccountId putVariable(String name, @Nullable Object val) {
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

    @SuppressWarnings("unchecked")
    @NotNull
    public <T> T asOwnerId() {
        return (T) id;
    }

}
