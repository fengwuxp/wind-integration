package com.wind.integration.workflow;

import com.wind.script.expression.ExpressionDescriptor;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工作流定义
 *
 * @author wuxp
 * @date 2025-12-19 14:13
 **/
@Data
public class WorkflowDefinition implements Serializable {

    /**
     * DSL 版本号
     */
    @NotNull
    private String version = "1.0.0";

    /**
     * 流转规则列表
     */
    @NotNull
    private List<Transition> transitions;

    /**
     * 流转规则
     */
    @Data
    public static class Transition implements Serializable {

        /**
         * 来源节点 ID
         */
        @NotNull
        private String source;

        /**
         * 目标节点 ID
         */
        @NotNull
        private String target;

        /**
         * 流程条件配置
         */
        @Deprecated(forRemoval = true)
        private ExpressionDescriptor condition;

        /**
         * 流程条件表达式
         */
        private String expression;

        /**
         * 触发动作，可选，例如 NOTIFY_NEXT
         */
        @NotNull
        private List<String> actions;

    }

}
