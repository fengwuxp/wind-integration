package com.wind.integration.metrics.dsl.expression;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.support.DataBindingMethodResolver;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 通过指标语法白名单验证的表达式编译句柄。
 *
 * <p>只保存 AST 与验证所得引用信息，不复制指标声明。宿主使用原值 DSL 与本次预先计算的值求值； 不接受数据加载函数，不公开 Spring AST 或任意构造入口。
 *
 * @author wuxp
 */
public final class CompiledMetricExpression {

    private static final MethodResolver INSTANCE_METHODS =
            DataBindingMethodResolver.forInstanceMethodInvocation();

    private static final PropertyAccessor MEASURE_VALUES = new MeasureValueAccessor();

    private final SpelExpression expression;

    private final Set<String> localValueFields;

    private final Set<MetricValueReference> metricValueReferences;

    private final boolean ratio;

    CompiledMetricExpression(
            SpelExpression expression,
            Set<String> localValueFields,
            Set<MetricValueReference> metricValueReferences,
            boolean ratio) {
        this.expression = expression;
        this.localValueFields = Collections.unmodifiableSet(new TreeSet<>(localValueFields));
        this.metricValueReferences =
                Collections.unmodifiableSet(new TreeSet<>(metricValueReferences));
        this.ratio = ratio;
    }

    /**
     * 取得编译时已确认的本指标 measure 引用。
     *
     * @return 按字段名排序且不可修改的引用集合
     */
    public Set<String> localValueFields() {
        return localValueFields;
    }

    /**
     * 取得派生表达式依赖的结果字段身份，不携带定义修订。
     *
     * @return 按指标编码和字段名排序、去重且不可修改的集合
     */
    public Set<MetricValueReference> metricValueReferences() {
        return metricValueReferences;
    }

    /**
     * 指示表达式是否使用 ratio，供宿主在加载数据前校验声明类型。
     *
     * @return 使用 ratio 时为 true，对应值须声明 DECIMAL 精度
     */
    public boolean ratio() {
        return ratio;
    }

    /**
     * 使用编译 AST 的规范文本进行表达式结构比较。
     *
     * @return 去除无意义排版差异的 AST 文本；不承诺代数等价化，也不重解析源文本
     */
    public String canonicalAst() {
        return expression.toStringAST();
    }

    /**
     * 在受限上下文中使用已提供的数值求值，不做最终类型归一或 orElse。
     *
     * @param definition 与本句柄对应的原值 DSL，提供 ratio 精度
     * @param measureValues 本指标已归一且尚未应用 orElse 的 measure 值
     * @param metricValues 宿主按冻结修订预先计算的依赖结果；仅允许读取已编译引用
     * @param path 值定义的 JSON Pointer，求值错误追加 /expression
     * @return 精确 Number 或正常 null；缺失值、非法操作和除零直接失败
     */
    public @Nullable Number evaluate(
            MetricValueDsl definition,
            Map<String, ?> measureValues,
            Map<MetricValueReference, ?> metricValues,
            String path) {
        MetricExpressionRoot root =
                new MetricExpressionRoot(
                        definition.scale(),
                        definition.roundingMode(),
                        referencedValues(localValueFields, measureValues, path),
                        referencedValues(metricValueReferences, metricValues, path));
        SimpleEvaluationContext context =
                SimpleEvaluationContext.forPropertyAccessors(MEASURE_VALUES)
                        .withAssignmentDisabled()
                        .withMethodResolvers(
                                (evaluationContext, target, name, arguments) ->
                                        target instanceof MetricExpressionRoot
                                                        && ("ratio".equals(name) || "metric".equals(name))
                                                ? INSTANCE_METHODS.resolve(
                                                        evaluationContext, target, name, arguments)
                                                : null)
                        .withRootObject(root)
                        .build();
        try {
            Object result = expression.getValue(context);
            if (result != null) {
                MetricExpressionRoot.exactDecimal(result);
            }
            return (Number) result;
        } catch (EvaluationException | IllegalArgumentException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ArithmeticException arithmetic) {
                    throw arithmetic;
                }
            }
            throw new MetricValidationException(
                    MetricErrorCode.RESULT_INVALID,
                    path + "/expression",
                    "Metric expression evaluation failed", exception);
        }
    }

    private static <K> Map<K, Object> referencedValues(
            Set<K> references, Map<K, ?> values, String path) {
        Map<K, Object> result = new LinkedHashMap<>();
        for (K reference : references) {
            if (!values.containsKey(reference)) {
                throw new MetricValidationException(
                        MetricErrorCode.RESULT_INVALID,
                        path + "/expression",
                        "Metric expression referenced value is missing: " + reference);
            }
            result.put(reference, values.get(reference));
        }
        return result;
    }

    /**
     * Spring 可缓存访问器；每次从当前根对象读取，避免跨次求值共享数据。
     */
    private static final class MeasureValueAccessor implements PropertyAccessor {

        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[] {MetricExpressionRoot.class};
        }

        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            return target instanceof MetricExpressionRoot root
                    && root.measureValues().containsKey(name);
        }

        @Override
        public TypedValue read(EvaluationContext context, Object target, String name)
                throws AccessException {
            if (!canRead(context, target, name)) {
                throw new AccessException("Metric measure value is not readable");
            }
            Object value = ((MetricExpressionRoot) target).measureValues().get(name);
            return new TypedValue(value == null ? null : MetricExpressionRoot.exactDecimal(value));
        }

        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) {
            return false;
        }

        @Override
        public void write(EvaluationContext context, Object target, String name, Object newValue)
                throws AccessException {
            throw new AccessException("Metric expression values are read-only");
        }
    }
}
