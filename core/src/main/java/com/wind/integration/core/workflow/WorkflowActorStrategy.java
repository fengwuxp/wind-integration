package com.wind.integration.core.workflow;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流程参与人策略
 */
@Getter
@AllArgsConstructor
public enum WorkflowActorStrategy implements DescriptiveEnum {
    /**
     * 用户
     */
    USER("用户"),

    /**
     * 角色
     */
    ROLE("角色"),

    /**
     * 用户组 （例如：部门）
     */
    GROUP("用户组"),

    /**
     * 表达式
     */
    EXPRESSION("表达式");

    private final String desc;

}