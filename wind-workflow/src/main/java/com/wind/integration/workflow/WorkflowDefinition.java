package com.wind.integration.workflow;

import com.wind.script.expression.ExpressionDescriptor;
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
    private String version = "1.0.0";

    /**
     * 流转规则列表
     */
    private List<Transition> transitions;

    /**
     * 流转规则
     */
    @Data
    public static class Transition implements Serializable{

        /**
         * 来源节点 ID
         */
        private String source;

        /**
         * 目标节点 ID
         */
        private String target;

        /**
         * 流程条件配置
         */
        private ExpressionDescriptor condition;

        /**
         * 触发动作，可选，例如 NOTIFY_NEXT
         */
        private List<String> actions;

    }

}
