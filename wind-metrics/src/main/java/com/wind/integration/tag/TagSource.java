package com.wind.integration.tag;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对象标签来源
 *
 * @author wuxp
 * @date 2024-09-11 09:57
 **/
@Getter
@AllArgsConstructor
public enum TagSource implements DescriptiveEnum {

    MANUAL(Integer.MAX_VALUE, "人工"),

    RISK_RULE(10000, "风控规则"),

    RULE_BASED(100, "打标规则");

    /**
     * 打标来源优先级，低优先级来源不能覆盖（更新）高优先级的标签
     */
    private final int priority;

    private final String desc;

}
