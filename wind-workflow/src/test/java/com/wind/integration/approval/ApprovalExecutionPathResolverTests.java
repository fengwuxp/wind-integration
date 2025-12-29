package com.wind.integration.approval;

import com.wind.integration.workflow.WorkflowDefinition;
import com.wind.script.expression.ExpressionDescriptor;
import com.wind.script.expression.Op;
import com.wind.script.expression.Operand;
import com.wind.script.expression.OperandType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author wuxp
 * @date 2025-12-19 15:32
 **/
class ApprovalExecutionPathResolverTests {

    @Test
    void testSingleNodePath() {
        ApprovalFlowDefinition flowDSL = new ApprovalFlowDefinition();

        // 节点
        ApprovalFlowDefinition.ApprovalNode node = new ApprovalFlowDefinition.ApprovalNode();
        node.setId("start");
        node.setName("开始审批");
        node.setType(ApprovalFlowDefinition.ApprovalNodeType.APPROVAL);

        flowDSL.setNodes(List.of(node));

        // 无 transitions
        flowDSL.setTransitions(Collections.emptyList());

        Map<String, Object> context = new HashMap<>();

        List<ApprovalFlowDefinition.ApprovalNode> paths = ApprovalExecutionPathResolver.eval(flowDSL, context);

        assertEquals(1, paths.size());
        assertEquals("start", paths.getFirst().getId());
    }

    @Test
    void testLinearPath() {
        ApprovalFlowDefinition flowDSL = new ApprovalFlowDefinition();

        // 节点
        ApprovalFlowDefinition.ApprovalNode node1 = new ApprovalFlowDefinition.ApprovalNode();
        node1.setId("start");
        node1.setType(ApprovalFlowDefinition.ApprovalNodeType.START);
        node1.setName("开始审批");

        ApprovalFlowDefinition.ApprovalNode node2 = new ApprovalFlowDefinition.ApprovalNode();
        node2.setId("finance");
        node2.setType(ApprovalFlowDefinition.ApprovalNodeType.APPROVAL);
        node2.setName("财务审批");

        ApprovalFlowDefinition.ApprovalNode node3 = new ApprovalFlowDefinition.ApprovalNode();
        node3.setId("ceo");
        node3.setType(ApprovalFlowDefinition.ApprovalNodeType.APPROVAL);
        node3.setName("CEO 审批");

        flowDSL.setNodes(List.of(node1, node2, node3));

        // transitions
        WorkflowDefinition.Transition t1 = new WorkflowDefinition.Transition();
        t1.setSource("start");
        t1.setTarget("finance");

        WorkflowDefinition.Transition t2 = new WorkflowDefinition.Transition();
        t2.setSource("finance");
        t2.setTarget("ceo");

        flowDSL.setTransitions(List.of(t1, t2));

        Map<String, Object> context = new HashMap<>();

        List<ApprovalFlowDefinition.ApprovalNode> paths = ApprovalExecutionPathResolver.eval(flowDSL, context);

        assertEquals(2, paths.size());
        assertEquals("finance", paths.get(0).getId());
        assertEquals("ceo", paths.get(1).getId());
    }

    @Test
    void testConditionalBranchPath() {
        ApprovalFlowDefinition flowDSL = new ApprovalFlowDefinition();

        // 节点
        ApprovalFlowDefinition.ApprovalNode start = new ApprovalFlowDefinition.ApprovalNode();
        start.setId("start");
        start.setType(ApprovalFlowDefinition.ApprovalNodeType.START);
        ApprovalFlowDefinition.ApprovalNode finance = new ApprovalFlowDefinition.ApprovalNode();
        finance.setId("finance");
        finance.setType(ApprovalFlowDefinition.ApprovalNodeType.APPROVAL);
        ApprovalFlowDefinition.ApprovalNode ceo = new ApprovalFlowDefinition.ApprovalNode();
        ceo.setId("ceo");
        ceo.setType(ApprovalFlowDefinition.ApprovalNodeType.APPROVAL);
        ApprovalFlowDefinition.ApprovalNode hr = new ApprovalFlowDefinition.ApprovalNode();
        hr.setId("hr");
        hr.setType(ApprovalFlowDefinition.ApprovalNodeType.APPROVAL);

        flowDSL.setNodes(List.of(start, finance, ceo, hr));

        //  条件 amount > 100000
        WorkflowDefinition.Transition t1 = new WorkflowDefinition.Transition();
        t1.setSource("start");
        t1.setTarget("finance");
        ExpressionDescriptor cond1 = new ExpressionDescriptor();
        cond1.setOp(Op.GT);
        cond1.setLeft(new Operand("amount", OperandType.VARIABLE));
        cond1.setRight(new Operand(100000, OperandType.CONSTANT));
        t1.setCondition(cond1);

        // 条件分支 amount <= 100000
        WorkflowDefinition.Transition t2 = new WorkflowDefinition.Transition();
        t2.setSource("start");
        t2.setTarget("hr");
        ExpressionDescriptor cond2 = new ExpressionDescriptor();
        cond2.setOp(Op.LE);
        cond2.setLeft(new Operand("amount", OperandType.VARIABLE));
        cond2.setRight(new Operand(100000, OperandType.CONSTANT));
        t2.setCondition(cond2);

        WorkflowDefinition.Transition t3 = new WorkflowDefinition.Transition();
        t3.setSource("finance");
        t3.setTarget("ceo");

        WorkflowDefinition.Transition t4 = new WorkflowDefinition.Transition();
        t4.setSource("hr");
        t4.setTarget("ceo");

        flowDSL.setTransitions(List.of(t1, t2, t3, t4));

        Map<String, Object> context = new HashMap<>();
        context.put("amount", 120000);

        List<ApprovalFlowDefinition.ApprovalNode> paths = ApprovalExecutionPathResolver.eval(flowDSL, context);

        assertEquals(List.of("finance", "ceo"),
                paths.stream().map(ApprovalFlowDefinition.ApprovalNode::getId).toList());

        // 测试 amount <= 100000
        context.put("amount", 80000);
        paths = ApprovalExecutionPathResolver.eval(flowDSL, context);
        assertEquals(List.of( "hr", "ceo"), paths.stream().map(ApprovalFlowDefinition.ApprovalNode::getId).toList());
    }
}
