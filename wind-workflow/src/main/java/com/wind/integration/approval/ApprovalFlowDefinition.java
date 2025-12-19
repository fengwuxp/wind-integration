package com.wind.integration.approval;

import com.wind.integration.workflow.WorkflowDefinition;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 审批流 DSL
 * 参见 https://www.yuque.com/suiyuerufeng-akjad/wind_ntegration/zk22gd5h5fuxtets
 *
 * @author wuxp
 * @date 2025-12-19 14:06
 **/
@Data
public class ApprovalFlowDefinition implements Serializable {

    /**
     * DSL 版本号
     */
    private String version = "1.0.0";

    /**
     * 全局配置
     */
    private GlobalConfig global;

    /**
     * 审批候选节点
     */
    @NotNull
    private List<ApprovalNode> nodes;

    /**
     * 流转规则列表
     */
    private List<WorkflowDefinition.Transition> transitions;

    /**
     * 全局配置
     */
    @Data
    public static class GlobalConfig implements Serializable {

        /**
         * 节点超时是否自动拒绝
         */
        private Boolean autoRejectOnTimeout;

        /**
         * 默认超时时间（小时）
         */
        private Integer defaultTimeoutHours;

        /**
         * 默认通知策略
         */
        private Notification notification;

    }

    @Data
    public static class Notification implements Serializable {

        /**
         * 通知渠道，例如 IN_APP, EMAIL
         */
        private List<String> channels;

    }

    /**
     * 审批节点定义
     */
    @Data
    public static class ApprovalNode implements Serializable {

        /**
         * 节点唯一 ID
         */
        private String id;

        /**
         * 节点类型，例如 APPROVAL
         */
        private String type;

        /**
         * 节点名称
         */
        private String name;

        /**
         * 审批人定义
         */
        private List<Actor> actors;

        /**
         * 决策策略
         */
        private DecisionPolicy decisionPolicy;

        /**
         * 节点行为，包括抄送、超时、钩子等
         */
        private NodeBehaviors behaviors;

    }

    /**
     * 流程参与人策略
     */
    public enum ActorStrategy {
        /**
         * 用户
         */
        USER,

        /**
         * 角色
         */
        ROLE,

        /**
         * 用户组
         */
        GROUP,

        /**
         * 表达式
         */
        EXPRESSION
    }

    /**
     * 审批人定义
     */
    @Data
    public static class Actor implements Serializable {

        /**
         * 策略
         */
        private ActorStrategy strategy;

        /**
         * 值，例如用户名、角色名或表达式
         */
        private String value;

    }

    /**
     * 决策策略模式
     */
    public enum DecisionPolicyMode {
        /**
         * 或签
         */
        ANY,

        /**
         * 会签
         */
        ALL,

        /**
         * 指定人数
         */
        COUNT,

        /**
         * 占比
         */
        RATIO
    }

    /**
     * 决策策略
     */
    @Data
    public static class DecisionPolicy implements Serializable {

        /**
         * 模式
         */
        private DecisionPolicyMode mode;

        /**
         * approveRequired：mode 为 COUNT/RATIO 时需要的数量
         */
        private Double approveRequired;

    }


    /**
     * 节点行为定义
     */
    @Data
    public static class NodeBehaviors implements Serializable {

        /**
         * 抄送列表
         */
        private List<CC> cc;

        /**
         * 超时设置
         */
        private Timeout timeout;

        /**
         * 钩子函数
         */
        private Hooks hooks;

    }

    /**
     * 抄送模式
     */
    public enum CCMode {

        /**
         * 进入节点时抄送
         */
        ON_ENTER,

        /**
         * 审批通过时抄送
         */
        AFTER_APPROVE,

        /**
         * 审批拒绝时抄送
         */
        AFTER_REJECT,

        /**
         * 节点完成时抄送
         */
        AFTER_COMPLETE
    }

    /**
     * 抄送定义
     */
    @Data
    public static class CC implements Serializable {

        /**
         * 抄送模式
         */
        private CCMode on;

        /**
         * 抄送人定义
         */
        private List<Actor> actors;
    }

    /**
     * 超时定义
     */
    @Data
    public static class Timeout implements Serializable {

        /**
         * 超时时间（小时）
         */
        private Integer hours;

        /**
         * 超时处理动作
         */
        private String action;
    }

    /**
     * 钩子定义
     */
    @Data
    public static class Hooks {

    }


}
