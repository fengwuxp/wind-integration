package com.wind.integration.workflow;


import com.wind.script.expression.ExpressionDescriptor;
import com.wind.script.expression.Op;
import com.wind.script.expression.Operand;
import com.wind.script.expression.OperandType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author wuxp
 * @date 2025-12-19 14:27
 **/
class WorkflowExecutionPathResolverTests {

    @Test
    void testSinglePathWithoutCondition() {
        WorkflowDefinition dsl = new WorkflowDefinition();
        dsl.setVersion("1.0");

        WorkflowDefinition.Transition t1 = new WorkflowDefinition.Transition();
        t1.setSource("start");
        t1.setTarget("end");
        t1.setCondition(null);

        dsl.setTransitions(List.of(t1));

        Map<String, Object> context = new HashMap<>();

        List<List<String>> paths = WorkflowExecutionPathResolver.eval(dsl, "start", context);

        assertEquals(1, paths.size());
        assertEquals(List.of("start", "end"), paths.getFirst());
    }

    @Test
    void testMultipleBranches() {
        WorkflowDefinition dsl = new WorkflowDefinition();
        dsl.setVersion("1.0");

        // start -> node1, start -> node2
        WorkflowDefinition.Transition t1 = new WorkflowDefinition.Transition();
        t1.setSource("start");
        t1.setTarget("node1");

        WorkflowDefinition.Transition t2 = new WorkflowDefinition.Transition();
        t2.setSource("start");
        t2.setTarget("node2");

        // node1 -> end, node2 -> end
        WorkflowDefinition.Transition t3 = new WorkflowDefinition.Transition();
        t3.setSource("node1");
        t3.setTarget("end");

        WorkflowDefinition.Transition t4 = new WorkflowDefinition.Transition();
        t4.setSource("node2");
        t4.setTarget("end");

        dsl.setTransitions(List.of(t1, t2, t3, t4));

        Map<String, Object> context = new HashMap<>();

        List<List<String>> paths = WorkflowExecutionPathResolver.eval(dsl, context);

        assertEquals(2, paths.size());
        assertTrue(paths.contains(List.of("start", "node1", "end")));
        assertTrue(paths.contains(List.of("start", "node2", "end")));
    }

    @Test
    void testConditionalPath() {
        WorkflowDefinition dsl = new WorkflowDefinition();
        dsl.setVersion("1.0");

        // start -> node1 [condition: amount > 100]
        WorkflowDefinition.Transition t1 = new WorkflowDefinition.Transition();
        t1.setSource("start");
        t1.setTarget("node1");
        ExpressionDescriptor cond1 = new ExpressionDescriptor();
        cond1.setOp(Op.GT);
        cond1.setLeft(new Operand("amount", OperandType.VARIABLE));
        cond1.setRight(new Operand(100, OperandType.CONSTANT));
        t1.setCondition(cond1);

        // start -> node2 [condition: amount <= 100]
        WorkflowDefinition.Transition t2 = new WorkflowDefinition.Transition();
        t2.setSource("start");
        t2.setTarget("node2");
        ExpressionDescriptor cond2 = new ExpressionDescriptor();
        cond2.setOp(Op.LE);
        cond2.setLeft(new Operand("amount", OperandType.VARIABLE));
        cond2.setRight(new Operand(100, OperandType.CONSTANT));
        t2.setCondition(cond2);

        // node1 -> end
        WorkflowDefinition.Transition t3 = new WorkflowDefinition.Transition();
        t3.setSource("node1");
        t3.setTarget("end");

        // node2 -> end
        WorkflowDefinition.Transition t4 = new WorkflowDefinition.Transition();
        t4.setSource("node2");
        t4.setTarget("end");

        dsl.setTransitions(List.of(t1, t2, t3, t4));

        Map<String, Object> context = new HashMap<>();
        context.put("amount", 120);

        List<List<String>> paths = WorkflowExecutionPathResolver.eval(dsl, "start", context);

        assertEquals(1, paths.size());
        assertEquals(List.of("start", "node1", "end"), paths.getFirst());

        // 测试 amount = 50
        context.put("amount", 50);
        paths = WorkflowExecutionPathResolver.eval(dsl, "start", context);
        assertEquals(1, paths.size());
        assertEquals(List.of("start", "node2", "end"), paths.getFirst());
    }

    @Test
    void testNoOutgoingTransitions() {
        WorkflowDefinition dsl = new WorkflowDefinition();
        dsl.setVersion("1.0");

        // 单节点，无 outgoing
        Map<String, Object> context = new HashMap<>();

        List<List<String>> paths = WorkflowExecutionPathResolver.eval(dsl, "start", context);

        assertEquals(0, paths.size());
    }
}
