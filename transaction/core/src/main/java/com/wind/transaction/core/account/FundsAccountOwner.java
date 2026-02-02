package com.wind.transaction.core.account;

import com.wind.common.exception.AssertUtils;
import com.wind.transaction.core.enums.FundsAccountOwnerType;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;

/**
 * 资金账户拥有者
 *
 * @param ownerId   主体唯一标识
 * @param ownerType 账号类型
 * @author wuxp
 * @date 2026-02-02 14:57
 */
public record FundsAccountOwner(@NonNull Serializable ownerId, @NonNull FundsAccountOwnerType ownerType) implements Serializable {

    public FundsAccountOwner {
        AssertUtils.notNull(ownerType, "argument ownerType must not null");
        AssertUtils.notNull(ownerId, "argument ownerId must not null");
    }

    public static FundsAccountOwner of(@NonNull Serializable ownerId, @NonNull FundsAccountOwnerType ownerType) {
        return new FundsAccountOwner(ownerId, ownerType);
    }

    public static FundsAccountOwner user(@NonNull Serializable userId) {
        return new FundsAccountOwner(userId, FundsAccountOwnerType.USER);
    }

    public static FundsAccountOwner merchant(@NonNull Serializable merchantId) {
        return new FundsAccountOwner(merchantId, FundsAccountOwnerType.MERCHANT);
    }


    @SuppressWarnings("unchecked")
    @NotNull
    public <T> T asOwnerId() {
        return (T) ownerId;
    }

}
