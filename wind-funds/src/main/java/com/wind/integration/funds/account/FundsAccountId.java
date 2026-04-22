package com.wind.integration.funds.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.common.enums.DescriptiveEnum;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;

/**
 * 资金账户标识
 *
 * @author wuxp
 * @date 2024-05-14 16:26
 **/
public interface FundsAccountId extends Serializable {

    /**
     * @return 资金账户ID
     */
    @NonNull
    String id();

    /**
     * @return 账户类型
     */
    @NonNull
    String type();

    /**
     * 创建不可变资金账户标识
     *
     * @param id   资金账户ID
     * @param type 资金账户类型
     * @return FundsAccountId 实例
     */
    @JsonCreator
    static FundsAccountId immutable(@NonNull @JsonProperty("id") String id, @JsonProperty("type") @NonNull String type) {
        return new ImmutableAccountId(id, type);
    }

    /**
     * 创建不可变资金账户标识
     *
     * @param id   资金账户ID
     * @param type 资金账户类型
     * @return FundsAccountId 实例
     */
    static FundsAccountId immutable(String id, DescriptiveEnum type) {
        return new ImmutableAccountId(id, type.name());
    }
}
