package com.wind.integration.metrics.spec;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricQueryParameterDsl;
import com.wind.integration.metrics.dsl.definition.MetricReferenceDsl;
import com.wind.integration.metrics.dsl.definition.MetricSubjectDsl;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
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
 * 一个精确修订的共同指标口径，统一承接 SQL/DSL 取值、原生表达式和跨指标派生。
 *
 * <p>取值差异只由 {@code valueQuery} 声明；主体、维度、参数、值类型、精度、空值规则和
 * 精确依赖在本定义中唯一保存。SCALAR 使用 value，FIELD_SET 使用 fields。
 * 原生指标可以在取值后运行本地表达式；纯派生不提供 valueQuery，而由表达式引用精确依赖。
 * SQL 返回列与 value/fields 的非表达式字段同名，不重复执行 measure 的聚合。</p>
 *
 * <p>依赖编码集合必须与表达式直接引用完全一致，同编码多字段共用一个修订；宿主校验发布资格、
 * 闭包、环与深度。读取路线由宿主固定，分段配置归物化计划，实际覆盖由已提交快照证明。
 * 本类不选版、不取数、不选择计划、不保存快照，不把 SQL/DSL 作为物化资格判据。</p>
 *
 * @param code 稳定且唯一的指标编码
 * @param revision 精确定义修订
 * @param valueShape 单值或多字段结构
 * @param subject 被统计主体；SQL 模板自行使用查询主体，field 可空
 * @param dimensions 完整维度定义
 * @param parameters 查询参数声明
 * @param valueQuery 原生取值声明；纯派生为空
 * @param value 单值口径；多字段为空，旧 SQL 未声明口径时也为空
 * @param fields 多字段口径；单值或旧 SQL 未声明时为空映射
 * @param dependencies 直接依赖的精确版本；原生为空，不存传递闭包
 * @author wuxp
 */
@Schema(description = "共同指标定义")
public record MetricDefinition(
        @Schema(description = "稳定指标编码") String code,
        @Schema(description = "精确定义修订") int revision,
        @Schema(description = "单值或多字段结构") MetricValueShape valueShape,
        @Schema(description = "被统计主体") MetricSubjectDsl subject,
        @Schema(description = "完整维度定义") List<String> dimensions,
        @Schema(description = "查询参数声明") Map<String, MetricQueryParameterDsl> parameters,
        @Schema(description = "原生取值声明；纯派生为空") @Nullable MetricValueQueryDefinition valueQuery,
        @Schema(description = "单值口径") @Nullable MetricValueDsl value,
        @Schema(description = "多字段口径") Map<String, MetricValueDsl> fields,
        @Schema(description = "直接依赖的精确版本") List<MetricReferenceDsl> dependencies) {

    public MetricDefinition {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(valueShape, "valueShape must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        dimensions = List.copyOf(dimensions);
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        validateDependencies(valueQuery, valueShape, value, fields, dependencies);
        if (valueQuery instanceof MetricSqlDefinition) {
            Map<String, MetricValueDsl> values = valueShape == MetricValueShape.SCALAR
                    ? Collections.singletonMap("value", value) : fields;
            values.forEach((field, definition) -> {
                if (definition == null || definition.measure() == null) {
                    return;
                }
                if (definition.measure().field() == null && definition.measure().filter() == null) {
                    return;
                }
                String path = valueShape == MetricValueShape.SCALAR ? "/metric/value"
                        : "/metric/fields/" + field.replace("~", "~0").replace("/", "~1");
                throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID,
                        path + "/measure", "SQL measures must not declare field or filter");
            });
        }
    }

    /**
     * @return 有取值声明时为 RAW，否则为依赖指标结果的 DERIVED。
     */
    public MetricDerivationType derivationType() {
        return valueQuery == null ? MetricDerivationType.DERIVED : MetricDerivationType.RAW;
    }

    /**
     * @return 共同主体类型，不根据 SQL/DSL 分支推导。
     */
    public String subjectType() {
        return subject.type();
    }

    // Jackson 反射调用；拒绝重复保存读取模式、分段和水位。
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
    @JsonAnySetter
    private void rejectUnknownProperty(String name, @Nullable Object ignored) {
        throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID,
                "/metric/" + name.replace("~", "~0").replace("/", "~1"), "Unknown metric definition property");
    }

    private static void validateDependencies(@Nullable MetricValueQueryDefinition valueQuery, MetricValueShape shape,
            @Nullable MetricValueDsl value, Map<String, MetricValueDsl> fields,
            List<MetricReferenceDsl> dependencies) {
        String path = "/metric/dependencies";
        if (valueQuery != null) {
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
