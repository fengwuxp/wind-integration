# 按业务条件选择审批路径

一张采购申请需要哪些人处理？假设部门主管审核所有申请，金额超过 10 万元时，还要财务复核。一旦主管通过，小额单可以结束，大额单必须等财务给出意见。金额决定走哪条路径，节点上的角色决定应用要去哪里找审批人。至于某张申请已经走到哪一步、谁作过决定，则要看该申请的实例和任务记录。

## 定义审批规则

把主管审核和财务复核分别定义为节点。节点有稳定 ID，可另外设置面向用户的名称。用**流转规则**连接源节点和目标节点，并在有分歧的边上写条件。采购示例从 `start` 进入 `manager`，再按同一份金额数据选择后续路径。

| 金额条件 | 节点路径 | 需要处理的审批节点 |
| --- | --- | --- |
| `#amount <= 100000` | `start → manager → end` | 主管 |
| `#amount > 100000` | `start → manager → finance → end` | 主管、财务 |

主管节点可以声明参与者策略 `ROLE`、值 `department-manager`。宿主应用结合申请所在部门和角色成员确定具体人员。参与者也可以用 `USER`、`GROUP` 或 `EXPRESSION` 表示；参与者表达式用于选人，流转条件用于选路，两者使用的业务事实可能不同。多个候选人如何形成节点决定，可以在定义里声明或签、会签、指定人数或比例。[参与者契约](../../core/src/main/java/com/wind/integration/core/workflow/WorkflowActorStrategy.java) · [审批定义](../../wind-workflow/src/main/java/com/wind/integration/approval/ApprovalFlowDefinition.java)

**需要展示所有可行路径**时，使用通用解析入口。它返回若干条有序节点 ID 路径，相同节点可以出现在不同路径里。**需要取得一次审批的处理顺序**时，使用审批解析入口。有流转规则时，它按规则列表顺序取得深度优先遍历的第一条路径，再选出其中定义过的审批和抄送节点。若两条出边同时成立，调换声明顺序就可能改变审批结果。业务要求只走一条时，像上表那样写成互斥条件；空白条件始终成立，把它写在最前面会优先命中。[通用路径解析](../../wind-workflow/src/main/java/com/wind/integration/workflow/WorkflowExecutionPathResolver.java) · [审批路径解析](../../wind-workflow/src/main/java/com/wind/integration/approval/ApprovalExecutionPathResolver.java)

## 从提交申请到留下审批结果

1. 提交申请时，应用取得本次采用的审批定义、金额等可信业务数据，将条件变量放入上下文。解析器同步返回路径，宿主据此创建审批实例，并保存定义版本和此次选定的路径，使后续处理可以沿用提交时的规则。
2. 进入主管节点时，应用解析候选人，建立待办。用户操作时，应用校验实际操作者资格，保存同意或拒绝的记录，并按该节点的决策策略判断是否可以推进。
3. 主管通过后，应用根据已选路径决定结束审批还是建立财务待办。审批拒绝、加签、抄送、通知和业务动作的执行时机均由宿主处理。若任务允许重复提交或跨进程恢复，实例状态和任务处理记录也是去重、恢复的依据。

审批定义可声明流程总时限、默认节点时限、节点专属时限，以及首次提醒小时数、重复间隔和最终动作（自动拒绝、自动通过或升级处理）。这些值是执行审批的输入。宿主应在实例启动或节点进入时按选用策略确定截止时间，并由自己的调度和状态更新逻辑执行到期处理。仅调用路径解析器不会启动计时或派发通知。[时限、提醒与动作定义](../../wind-workflow/src/main/java/com/wind/integration/approval/ApprovalFlowDefinition.java)

## 接入并运行示例

应用引入 `com.wind.integration:wind-workflow`，版本可由 `wind-integration-dependencies` 管理。模块传递引入流程参与者契约和表达式求值依赖。下例是可直接在应用类路径上运行的 Java 主类，使用内存中的定义，打印两种金额实际选中的审批节点。[模块依赖](../../wind-workflow/pom.xml)

```java
import com.wind.integration.approval.ApprovalExecutionPathResolver;
import com.wind.integration.approval.ApprovalFlowDefinition;
import com.wind.integration.approval.ApprovalFlowDefinition.ApprovalNode;
import com.wind.integration.approval.ApprovalFlowDefinition.ApprovalNodeType;
import com.wind.integration.workflow.WorkflowDefinition.Transition;

import java.util.List;
import java.util.Map;

public class PurchaseApprovalExample {
    public static void main(String[] args) {
        var flow = new ApprovalFlowDefinition();
        flow.setNodes(List.of(node("start", ApprovalNodeType.START),
                node("manager", ApprovalNodeType.APPROVAL), node("finance", ApprovalNodeType.APPROVAL),
                node("end", ApprovalNodeType.END)));
        flow.setTransitions(List.of(edge("start", "manager", null),
                edge("manager", "end", "#amount <= 100000"),
                edge("manager", "finance", "#amount > 100000"), edge("finance", "end", null)));

        for (int amount : List.of(100000, 100001)) {
            var approvals = ApprovalExecutionPathResolver.eval(flow, Map.of("amount", amount));
            System.out.println(amount + " " + approvals.stream().map(ApprovalNode::getId).toList());
        }
    }

    private static ApprovalNode node(String id, ApprovalNodeType type) {
        var node = new ApprovalNode();
        node.setId(id);
        node.setType(type);
        return node;
    }

    private static Transition edge(String source, String target, String condition) {
        var edge = new Transition();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setExpression(condition);
        return edge;
    }
}
```

输出为 `100000 [manager]` 和 `100001 [manager, finance]`。示例只设置路径计算会读取的字段。应用若对完整定义执行 Bean Validation，需按自己的校验入口补齐节点名称、策略和全局配置等必填字段。

## 默认值和定义错误

| 情况 | 当前解析结果与接入约束 |
| --- | --- |
| 默认版本、起点 | 定义的版本字段初值为 `1.0.0`。解析器默认从字面 ID `start` 开始，通用入口可显式传入其他起点。审批入口始终使用默认起点 |
| 条件值 | 空白条件无条件通过。其他条件交给 Spring 表达式求值，仅返回布尔 `true` 才沿边前进；表达式执行抛错会中断本次解析 |
| 当前节点没有可走的边 | 定义中有流转规则时，通用入口返回截至当前节点的路径。即使没有到达 `END`，该路径也可出现在结果中。业务要求显式终点时，由宿主检查 |
| 完全没有流转规则 | 通用入口返回空路径集合。审批入口则原样返回定义中的 `nodes`，保留声明顺序及 `START`、`END` 等所有节点，也可能得到 `null` |
| 重复节点、未知节点或环 | 有流转规则时，重复的节点 ID 会使审批节点索引构建失败，路径上的未知节点 ID 会在审批结果中过滤。通用遍历没有环检测，可达且可通行的环会导致无限递归直至栈溢出。定义发布时应校验 ID 唯一、引用有效和路径无环 |

仓库用例可用来对照空定义、线性流程、条件分支和多路径的行为。[通用路径用例](../../wind-workflow/src/test/java/com/wind/integration/workflow/WorkflowExecutionPathResolverTests.java) · [审批路径用例](../../wind-workflow/src/test/java/com/wind/integration/approval/ApprovalExecutionPathResolverTests.java)
