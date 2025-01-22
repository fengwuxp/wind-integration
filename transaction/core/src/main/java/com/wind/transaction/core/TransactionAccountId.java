package com.wind.transaction.core;

import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

/**
 * 交易账户标识
 *
 * @author wuxp
 * @date 2024-05-14 16:26
 **/
@EqualsAndHashCode
@ToString
public class TransactionAccountId {

    /**
     * 账号唯一标识
     */
    @Getter
    private final String id;

    /**
     * 账号类型
     */
    @Getter
    private final String type;

    /**
     * 归属的用户
     */
    @NotNull
    private final Object userId;

    private TransactionAccountId(@NotNull String id, @NotNull String type, @NotNull Object userId) {
        AssertUtils.hasText(id, "argument id must not empty");
        AssertUtils.hasText(type, "argument type must not empty");
        AssertUtils.notNull(userId, "argument userId must not null");
        this.id = id;
        this.type = type;
        this.userId = userId;
    }

    public static TransactionAccountId of(String id, Object userId) {
        return of(id, WindConstants.DEFAULT_TEXT, userId);
    }

    public static TransactionAccountId of(String id, String type, Object userId) {
        return new TransactionAccountId(id, type, userId);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public <T> T getUserId() {
        return (T) userId;
    }

}
