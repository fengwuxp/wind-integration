package com.wind.integration.funds.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.common.exception.AssertUtils;
import org.jspecify.annotations.NonNull;

/**
 * @param id   账号唯一标识
 * @param type 账户类型
 * @author wuxp
 * @date 2026-04-22 09:16
 */
record ImmutableAccountId(@NonNull String id, @NonNull String type) implements FundsAccountId {

    @JsonCreator
    public ImmutableAccountId(@NonNull @JsonProperty("id") String id, @JsonProperty("type") @NonNull String type) {
        AssertUtils.hasText(id, "argument id must not empty");
        AssertUtils.hasText(type, "argument type must not empty");
        this.id = id;
        this.type = type;
    }
}
