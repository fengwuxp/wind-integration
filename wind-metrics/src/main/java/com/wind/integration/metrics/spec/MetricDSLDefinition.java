package com.wind.integration.metrics.spec;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricJoinDsl;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricTimeDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;
import com.wind.integration.metrics.enums.MetricDerivationType;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.expression.MetricExpressionCompiler;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 指标计算口径，描述事实聚合和跨指标派生表达式。
 *
 * <p>事实指标必须提供 {@code fact} 和 {@code time}；派生指标不提供事实字段，改由表达式取值。
 * {@code SCALAR} 只使用 {@code value}，
 * {@code FIELD_SET} 只使用 {@code fields}。</p>
 *
 * <p>派生指标在 {@code value.expression} 或 {@code fields.*.expression} 中使用
 * {@code metric('METRIC_CODE', 'valueField')} 引用其他指标；引用单值指标时字段名为
 * {@code value}。例如二级指标使用
 * {@code ratio(metric('APPROVED_COUNT', 'value'), metric('TOTAL_COUNT', 'value'))}，
 * 更高层指标可以继续引用该二级指标。</p>
 *
 * <p>{@link com.wind.integration.metrics.expression.MetricExpressionCompiler#compile}
 * 从表达式提取直接引用，结果由
 * {@link com.wind.integration.metrics.expression.MetricExpression#metricValueReferences()}
 * 提供。{@code dependencies} 只保存每个直接引用编码的精确版本，其编码集合必须与
 * 表达式完全一致；字段仍由表达式决定，同编码多字段共用一个版本。宿主负责确认已发布目标、
 * 冻结选择、展开传递闭包、校验环和深度，以及执行与物化能力检查；不追随最新版本。</p>
 *
 * <p>读取模式由宿主指标元信息统一维护，不在计算定义中复制。分段配置只保存在
 * {@link com.wind.integration.metrics.dsl.materialization.MetricMaterializationPlanDsl} 中；
 * 宿主在本次一致性读取边界内按指标编码和定义修订固定读取路线；快照路线通过成员关系解析唯一已发布计划。
 * 快照查询可读取计划的来源顺序和固定边界，并按实际时间合并，不枚举 {@code YEAR}/{@code MONTH}
 * 理论时间片或用配置窗口替代本次实际查询边界。
 * 派生指标继承精确依赖的读取方式，不单独声明模式。</p>
 *
 * <p>物化执行器依据分段规则计算待快照范围；快照查询使用本次已固定的计划和实际已提交覆盖。
 * 物化计划引用精确定义，共同保存的成员共享同一套分段规则。</p>
 *
 * @param code 稳定且唯一的指标编码
 * @param revision 定义修订号，与编码共同唯一标识一个定义实例
 * @param valueShape 指标值结构
 * @param fact 主事实源编码；派生指标为空
 * @param joins 主事实源关联定义，最多两个
 * @param subject 被统计主体定义
 * @param time 主事实源时间字段；派生指标为空
 * @param dimensions 聚合维度字段引用
 * @param parameters 查询参数定义
 * @param rowSelection 所有 measure 共享的聚合前有限行集
 * @param value 单值指标定义；多字段指标为空
 * @param fields 多字段指标定义；单值指标为空映射
 * @param dependencies 派生表达式直接引用的精确版本；事实指标为空，不包含传递闭包
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Schema(description = "通过 DSL 定义的指标")
public record MetricDSLDefinition(
        @Schema(description = "稳定且唯一的指标编码") String code,
        @Schema(description = "定义修订号，与编码共同唯一标识一个定义实例") int revision,
        @Schema(description = "指标值结构") MetricValueShape valueShape,
        @Nullable @Schema(description = "主事实源编码；派生指标为空") String fact,
        @Schema(description = "主事实源关联定义") List<MetricJoinDsl> joins,
        @Schema(description = "被统计主体定义") MetricSubjectDsl subject,
        @Nullable @Schema(description = "主事实源时间字段；派生指标为空") MetricTimeDsl time,
        @Schema(description = "聚合维度字段引用") List<String> dimensions,
        @Schema(description = "查询参数定义") Map<String, MetricQueryParameterDsl> parameters,
        @Nullable @Schema(description = "所有 measure 共享的聚合前有限行集") MetricRowSelectionDsl rowSelection,
        @Nullable @Schema(description = "单值指标定义；多字段指标为空") MetricValueDsl value,
        @Schema(description = "多字段指标定义；单值指标为空映射") Map<String, MetricValueDsl> fields,
        @Schema(description = "派生表达式直接引用的精确版本；事实指标为空") List<MetricReferenceDsl> dependencies) implements MetricDefinitionObject {

    public MetricDSLDefinition {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(valueShape, "valueShape must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        joins = List.copyOf(joins);
        dimensions = List.copyOf(dimensions);
        parameters = immutableMap(parameters);
        fields = immutableMap(fields);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        validateDependencies(fact, valueShape, value, fields, dependencies);
    }

    /**
     * 兼容无依赖的事实指标构造；派生指标必须使用含精确版本绑定的构造器。
     */
    public MetricDSLDefinition(String code, int revision, MetricValueShape valueShape,
            @Nullable String fact, List<MetricJoinDsl> joins, MetricSubjectDsl subject,
            @Nullable MetricTimeDsl time, List<String> dimensions, Map<String, MetricQueryParameterDsl> parameters,
            @Nullable MetricRowSelectionDsl rowSelection, @Nullable MetricValueDsl value,
            Map<String, MetricValueDsl> fields) {
        this(code, revision, valueShape, fact, joins, subject, time, dimensions, parameters,
                rowSelection, value, fields, List.of());
    }

    @Override
    public MetricDerivationType derivationType() {
        return fact == null ? MetricDerivationType.DERIVED : MetricDerivationType.RAW;
    }

    @Override
    public String subjectType() {
        return subject.type();
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    // Jackson 反射调用；拒绝 executionMode、segments 等非计算属性，避免形成重复配置源。
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
    @JsonAnySetter
    private void rejectUnknownProperty(String name, @Nullable Object value) {
        throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID,
                "/metric/" + name.replace("~", "~0").replace("/", "~1"), "Unknown metric definition property");
    }

    private static void validateDependencies(@Nullable String fact, MetricValueShape shape,
            @Nullable MetricValueDsl value, Map<String, MetricValueDsl> fields,
            List<MetricReferenceDsl> dependencies) {
        String path = "/metric/dependencies";
        if (fact != null) {
            if (!dependencies.isEmpty()) {
                throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID, path,
                        "RAW metrics must not bind dependencies");
            }
            return;
        }
        Set<String> selectedCodes = new TreeSet<>();
        for (MetricReferenceDsl dependency : dependencies) {
            if (!selectedCodes.add(dependency.metricCode())) {
                throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID, path,
                        "Each dependency metricCode must select exactly one revision");
            }
        }
        if (selectedCodes.isEmpty()) {
            throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID, path,
                    "DERIVED metrics require exact dependency revisions");
        }
        Map<String, MetricValueDsl> values = shape == MetricValueShape.SCALAR
                ? Collections.singletonMap("value", value) : fields;
        Set<String> referencedCodes = new TreeSet<>();
        MetricExpressionCompiler compiler = new MetricExpressionCompiler();
        values.forEach((field, definition) -> {
            String fieldPath = shape == MetricValueShape.SCALAR ? "/metric/value"
                    : "/metric/fields/" + field.replace("~", "~0").replace("/", "~1");
            if (definition == null || definition.expression() == null || definition.measure() != null) {
                throw new MetricValidationException(MetricErrorCode.DSL_VALUE_BRANCH_INVALID, fieldPath,
                        "DERIVED values require an expression and must not contain a measure");
            }
            compiler.compile(definition.expression(), Set.of(), fieldPath + "/expression")
                    .metricValueReferences().forEach(reference -> referencedCodes.add(reference.metricCode()));
        });
        if (!selectedCodes.equals(referencedCodes)) {
            throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID, path,
                    "Dependency metricCodes must exactly match direct expression references");
        }
    }
}
