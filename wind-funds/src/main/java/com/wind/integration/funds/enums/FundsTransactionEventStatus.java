package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资金账户交易事件处理状态枚举
 *
 * @author wuxp
 * @date 2026-04-15 15:27
 **/
@AllArgsConstructor
@Getter
public enum FundsTransactionEventStatus implements DescriptiveEnum {

    /**
     * 初始化（已创建未进入调度）
     */
    INIT("初始化"),

    /**
     * 已就绪（可执行）
     */
    PENDING("待执行"),

    /**
     * 执行中
     */
    PROCESSING("处理中"),

    /**
     * 成功
     */
    SUCCESS("成功"),

    /**
     * 可重试失败
     */
    FAILED("失败（可重试）"),

    /**
     * 永久失败（死信）
     */
    DEAD_LETTER("失败（不可重试）"),

    /**
     * 业务取消
     */
    CANCELED("已取消");

    private final String desc;
}
