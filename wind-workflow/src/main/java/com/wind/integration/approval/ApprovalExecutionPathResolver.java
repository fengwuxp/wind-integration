package com.wind.integration.approval;

import com.wind.integration.workflow.WorkflowExecutionPathResolver;
import org.jspecify.annotations.NonNull;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批流节点执行路径计算器
 *
 * @author wuxp
 * @date 2025-12-19 14:39
 **/
public final class ApprovalExecutionPathResolver {

    private ApprovalExecutionPathResolver() {
        throw new AssertionError();
    }

    /**
     * @param flowDefinition 审批流 DSL
     * @param context        执行上下文变量
     * @return 符合条件的节点执行路径（列表嵌套表示分支），仅包含审批或抄送节点
     */
    @NonNull
    public static List<ApprovalFlowDefinition.ApprovalNode> eval(ApprovalFlowDefinition flowDefinition, Map<String, Object> context) {
        if (CollectionUtils.isEmpty(flowDefinition.getTransitions())) {
            return flowDefinition.getNodes();
        }

        // 使用现有的节点路径计算器获取 ID 路径
        List<List<String>> nodeIdPaths = WorkflowExecutionPathResolver.eval(flowDefinition, context);
        if (nodeIdPaths.isEmpty()) {
            return Collections.emptyList();
        }

        // 将节点 ID 转换为 ApprovalNode 对象
        Map<String, ApprovalFlowDefinition.ApprovalNode> nodes = flowDefinition.getNodes()
                .stream()
                .collect(Collectors.toMap(ApprovalFlowDefinition.ApprovalNode::getId, Function.identity()));

        List<ApprovalFlowDefinition.ApprovalNode> result = new ArrayList<>();
        for (String nodeId : nodeIdPaths.getFirst()) {
            ApprovalFlowDefinition.ApprovalNode node = nodes.get(nodeId);
            if (node != null && (node.getType() == ApprovalFlowDefinition.ApprovalNodeType.APPROVAL || node.getType() == ApprovalFlowDefinition.ApprovalNodeType.CC)) {
                result.add(node);
            }
        }
        return result;
    }

}
