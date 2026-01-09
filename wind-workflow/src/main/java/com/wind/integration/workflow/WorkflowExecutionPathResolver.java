package com.wind.integration.workflow;


import com.wind.script.spring.SpringExpressionEvaluator;
import org.jspecify.annotations.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流路径计算器
 * 仅计算符合条件的节点流转路径，不执行动作
 *
 * @author wuxp
 * @date 2025-12-19 14:19
 **/
public final class WorkflowExecutionPathResolver {

    private WorkflowExecutionPathResolver() {
        throw new AssertionError();
    }

    /**
     * 默认从入口节点开始计算
     *
     * @param dsl     工作流 dsl
     * @param context 执行上下文
     * @return 符合条件的节点执行路径（列表嵌套表示分支），可能返回多条执行路径
     */
    @NonNull
    public static List<List<String>> eval(@NonNull WorkflowDefinition dsl, @NonNull Map<String, Object> context) {
        return eval(dsl, "start", context);
    }

    /**
     * 计算从入指定节点开始的执行路径
     *
     * @param dsl         工作流 dsl
     * @param startNodeId 起始节点 ID
     * @param context     执行上下文
     * @return 符合条件的节点执行路径（列表嵌套表示分支）
     */
    @NonNull
    public static List<List<String>> eval(@NonNull WorkflowDefinition dsl, @NonNull String startNodeId, @NonNull Map<String, Object> context) {
        List<List<String>> paths = new ArrayList<>();
        dfs(startNodeId, dsl.getTransitions(), context, new ArrayList<>(), paths);
        return paths;
    }

    private static void dfs(String nodeId, List<WorkflowDefinition.Transition> transitions, Map<String, Object> context, List<String> currentPath, List<List<String>> paths) {
        if (CollectionUtils.isEmpty(transitions)) {
            return;
        }
        currentPath.add(nodeId);
        List<WorkflowDefinition.Transition> outgoing = transitions.stream()
                .filter(t -> t.getSource().equals(nodeId))
                .toList();
        boolean hasNext = false;
        for (WorkflowDefinition.Transition item : outgoing) {
            boolean canMoveNext = !StringUtils.hasText(item.getExpression()) || Objects.equals(true, SpringExpressionEvaluator.DEFAULT.eval(item.getExpression(), context));
            if (canMoveNext) {
                dfs(item.getTarget(), transitions, context, currentPath, paths);
                hasNext = true;
            }
        }
        if (!hasNext) {
            // 当前路径抵达终点，添加到路径集合中
            paths.add(new ArrayList<>(currentPath));
        }
        currentPath.removeLast();
    }
}
