package com.wind.integration.approval;

import com.wind.common.enums.DescriptiveEnum;
import com.wind.integration.core.workflow.WorkflowActor;
import com.wind.integration.workflow.WorkflowDefinition;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批流 DSL
 * 参见 https://www.yuque.com/suiyuerufeng-akjad/wind_ntegration/zk22gd5h5fuxtets
 *
 * @author wuxp
 * @date 2025-12-19 14:06
 **/
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ApprovalFlowDefinition extends WorkflowDefinition {

    /**
     * DSL 版本号
     */
    @NotNull
    private String version = "1.0.0";

    /**
     * 全局配置
     */
    @NotNull
    private GlobalConfig global;

    /**
     * 审批候选节点
     */
    @NotNull
    private List<ApprovalNode> nodes;

    /**
     * 流程执行动作列表
     */
    private List<FlowAction> actions;


    /**
     * 节点任务触发时机
     */
    @AllArgsConstructor
    @Getter
    public enum FlowActionTrigger implements DescriptiveEnum {

        ON_FLOW_START("流程开始"),

        ON_FLOW_APPROVED("审批通过"),

        ON_FLOW_REFUSED("审批拒绝"),

        ON_FLOW_TERMINATED("审批终止"),

        ON_FLOW_EXPIRED("审批超时");

        private final String desc;
    }

    /**
     * 流程任务
     */
    @Data
    public static class FlowAction implements Serializable {

        private FlowActionTrigger trigger;

        private String action;

        private Map<String, Object> params;
    }


    /**
     * 超时处理动作（最终结果）
     */
    @Getter
    @AllArgsConstructor
    public enum ApprovalTimeoutAction implements DescriptiveEnum {

        /**
         * 自动拒绝
         */
        AUTO_REFUSE("自动拒绝"),

        /**
         * 自动通过
         */
        AUTO_APPROVE("自动通过"),

        /**
         * 升级处理
         */
        ESCALATE("升级处理");

        private final String desc;
    }

    /**
     * 超时提醒配置
     */
    @Data
    public static class TimeoutReminder implements Serializable {

        /**
         * 第一次提醒（小时）
         */
        private Integer firstReminderHours;

        /**
         * 重复提醒间隔（小时）
         */
        private Integer repeatIntervalHours;

        /**
         * 通知对象
         */
        @NotNull
        private WorkflowActor notifyActor;

        /**
         * 通知策略
         */
        @NotNull
        private Notification notification;
    }

    @Data
    public static class TimeoutPolicy implements Serializable {

        /**
         * 超时时间（小时）
         */
        @NotNull
        private Integer timeoutHours;

        /**
         * 提醒策略
         */
        private TimeoutReminder reminder;

        /**
         * 最终处理动作
         */
        @NotNull
        private ApprovalTimeoutAction finalAction;

    }

    /**
     * 全局配置
     */
    @Data
    public static class GlobalConfig implements Serializable {

        /**
         * 审批单超时处理策略
         */
        @NotNull
        private TimeoutPolicy timeoutPolicy;

        /**
         * 默认节点超时策略（节点未配置时生效）
         */
        @NotNull
        private TimeoutPolicy defaultNodeTimeoutPolicy;

        /**
         * 默认抄送人
         */
        private List<WorkflowActor> defaultCc;

        /**
         * 默认通知策略
         */
        private Notification defaultNotification;

    }

    @Data
    public static class Notification implements Serializable {

        /**
         * 通知渠道，例如 IN_APP, EMAIL, DINGTALK
         */
        @NotNull
        private List<String> channels;

    }

    /**
     * 节点加签策略
     */
    @AllArgsConstructor
    @Getter
    public enum NodeAddSignPolicy implements DescriptiveEnum {

        NONE("不允许加签"),

        PRE("允许前加签"),

        POST("允许后加签"),

        PRE_OR_POST("前/后加签均允许");

        private final String desc;
    }


    /**
     * 节点类型
     */
    @AllArgsConstructor
    @Getter
    public enum ApprovalNodeType implements DescriptiveEnum {

        START("开始"),

        APPROVAL("审批"),

        CC("抄送"),

        END("结束");

        private final String desc;

    }

    /**
     * 审批节点定义
     */
    @Data
    public static class ApprovalNode implements Serializable {

        /**
         * 节点唯一 ID
         */
        @NotNull
        private String id;

        /**
         * 节点类型
         * 例如 APPROVAL、CC
         */
        @NotNull
        private ApprovalNodeType type;

        /**
         * 节点名称
         */
        @NotNull
        private String name;

        /**
         * 审批人定义
         */
        private List<WorkflowActor> actors;

        /**
         * 决策策略
         */
        @NotNull
        private DecisionPolicy decisionPolicy;

        /**
         * 加签策略
         */
        @NotNull
        private NodeAddSignPolicy signPolicy;

        /**
         * 抄送列表
         */
        private CC cc;

        /**
         * 节点超时处理策略
         */
        private TimeoutPolicy timeoutPolicy;

        /**
         * 节点通过执行的任务
         */
        private Set<String> actions;

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
        @NotNull
        private DecisionPolicyMode mode;

        /**
         * approveRequired：mode 为 COUNT/RATIO 时需要的数量
         */
        private Double approveRequired;

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
        AFTER_REFUSED,
    }

    /**
     * 抄送定义
     */
    @Data
    public static class CC implements Serializable {

        /**
         * 抄送模式
         */
        @NotNull
        private CCMode on;

        /**
         * 抄送人定义
         */
        @NotNull
        private List<WorkflowActor> actors;
    }

}
