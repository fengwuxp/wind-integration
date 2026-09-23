package com.wind.integration.metrics.expression;

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
 * 可重复求值的指标表达式。
 *
 * <p>外部调用方通过 {@link MetricExpressionCompiler#compile} 获取本对象。构造器仅包内可见，
 * AST 和引用信息由编译器校验后共同生成，不允许外部自行拼装未校验的句柄。</p>
 *
 * <p>句柄只保存 AST 和引用信息；每次 {@link #evaluate} 创建独立的求值根对象和受限上下文，
 * 使用本次值与精度。宿主负责预先加载依赖；本对象不保存主体数据、不选择版本、不做最终
 * 类型归一或 orElse 处理。ratio 函数的除法使用本次值定义中的精度。</p>
 *
 * @author wuxp
 */
public final class MetricExpression {

    private static final MethodResolver INSTANCE_METHODS =
            DataBindingMethodResolver.forInstanceMethodInvocation();

    private static final PropertyAccessor VALUE_ACCESSOR = new ExpressionValueAccessor();

    private final SpelExpression expression;

    private final Set<String> localValueFields;

    private final Set<MetricValueReference> metricValueReferences;

    private final boolean usesRatio;

    MetricExpression(
            SpelExpression expression,
            Set<String> localValueFields,
            Set<MetricValueReference> metricValueReferences,
            boolean usesRatio) {
        this.expression = expression;
        this.localValueFields = Collections.unmodifiableSet(new TreeSet<>(localValueFields));
        this.metricValueReferences =
                Collections.unmodifiableSet(new TreeSet<>(metricValueReferences));
        this.usesRatio = usesRatio;
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
    public boolean usesRatio() {
        return usesRatio;
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
                SimpleEvaluationContext.forPropertyAccessors(VALUE_ACCESSOR)
                        .withAssignmentDisabled()
                        .withMethodResolvers(
                                (evaluationContext, target, name, arguments) ->
                                        target instanceof MetricExpressionRoot
                                                        && (MetricExpressionRoot.RATIO_FUNCTION.equals(name)
                                                                || MetricExpressionRoot.METRIC_FUNCTION.equals(name))
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
     * 只暴露编译期确认的本地 measure 属性；每次从当前求值根读取，避免跨次求值共享数据。
     */
    private static final class ExpressionValueAccessor implements PropertyAccessor {

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
