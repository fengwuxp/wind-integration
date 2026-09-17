package com.wind.integration.metrics.dsl;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricDefinitionSpec;
import com.wind.integration.metrics.dsl.definition.MetricDslSpec;
import com.wind.integration.metrics.dsl.definition.MetricValueDsl;
import com.wind.integration.metrics.dsl.literal.DecimalMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricValueShape;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * 直接按指标 DSL 合并原始 measure、归一精确数值并完成最终空值处理。
 *
 * <p>输入定义须已通过 DSL 校验。本类不加载数据或解释表达式语言；表达式回调由宿主使用 本次冻结的 AST 和已计算依赖执行。分段合并不舍入、不计算表达式、不应用 orElse。
 *
 * @author wuxp
 */
public final class MetricValueCalculator {

    /**
     * 在读取任何分段之前验证当前 measure 是否具有足够的标量合并状态。
     *
     * @param definition 已校验的原始事实指标定义
     * @throws MetricValidationException 派生指标、无 measure、rowSelection 或 AVG 不支持分段合并
     */
    public void validateMergeable(MetricDslSpec definition) {
        if (definition.fact() == null) {
            throw invalid("/metric/fact", "Derived metric has no fact measures to merge");
        }
        if (definition.rowSelection() != null) {
            throw new MetricValidationException(
                    MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED,
                    "/metric/rowSelection",
                    "Metric rowSelection only supports a REALTIME query segment");
        }
        Map<String, MetricValueDsl> measures = measures(definition);
        if (measures.isEmpty()) {
            throw invalid("/metric/value", "Fact metric does not contain measure values");
        }
        measures.forEach(
                (field, value) -> {
                    if (value.measure().aggregation() == MetricAggregation.AVG) {
                        throw new MetricValidationException(
                                MetricErrorCode.METRIC_EXECUTION_MODE_UNSUPPORTED,
                                path(definition, field) + "/measure/aggregation",
                                "AVG cannot be merged without SUM and COUNT aggregation state");
                    }
                });
    }

    /**
     * 精确合并同一冻结定义的原始 measure，各分段须已由宿主完成覆盖及读取校验。
     *
     * @param definition 已校验的原始事实指标定义
     * @param segments 至少一个分段；每个 Map 的字段集合恰好等于 DSL measure 集合
     * @return 按字段名排序的不可修改 Map；COUNT 不为空，其余聚合允许全空结果
     */
    public Map<String, @Nullable Number> merge(
            MetricDslSpec definition,
            List<? extends Map<String, ? extends @Nullable Number>> segments) {
        validateMergeable(definition);
        if (segments.isEmpty()) {
            throw invalid("", "Metric measure merge requires at least one segment");
        }
        Map<String, MetricValueDsl> measures = measures(definition);
        Map<String, @Nullable Number> result = new LinkedHashMap<>();
        measures.forEach(
                (field, value) ->
                        result.put(
                                field,
                                value.measure().aggregation() == MetricAggregation.COUNT
                                        ? BigInteger.ZERO
                                        : null));
        for (Map<String, ? extends @Nullable Number> segment : segments) {
            requireFields(measures, segment);
            measures.forEach(
                    (field, value) ->
                            result.put(
                                    field,
                                    merge(
                                            value.measure().aggregation(),
                                            result.get(field),
                                            segment.get(field),
                                            path(definition, field))));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 按声明类型与精度归一单个数值，不处理 orElse。
     *
     * @param definition 原始值定义
     * @param value 精确 Number 或正常 null；不接受 Float、Double 或自定义 Number
     * @param path 用于报告错误的 JSON Pointer
     * @return Integer、Long、BigDecimal 或正常 null；溢出与小数截断显式失败
     */
    public @Nullable Number normalize(
            MetricValueDsl definition, @Nullable Number value, String path) {
        if (value == null) {
            return null;
        }
        BigDecimal decimal = exactDecimal(value, path);
        try {
            return switch (definition.valueType()) {
                case INTEGER -> decimal.toBigIntegerExact().intValueExact();
                case LONG -> decimal.toBigIntegerExact().longValueExact();
                case DECIMAL -> decimal.setScale(definition.scale(), definition.roundingMode());
                case STRING, TIMESTAMP -> throw new MetricValidationException(
                        MetricErrorCode.RESULT_INVALID,
                        path,
                        "Numeric normalization not supported for " + definition.valueType());
            };
        } catch (ArithmeticException exception) {
            throw new MetricValidationException(
                    MetricErrorCode.RESULT_INVALID,
                    path,
                    "Metric value is outside its declared numeric type",
                    exception);
        }
    }

    /**
     * 在所有 measure 就绪后，依次归一 measure、计算表达式、应用最终 orElse。
     *
     * @param definition 已校验的原始定义；派生指标不提供原始 measure
     * @param rawMeasures 原始 measure 值，字段缺失不同于正常 null
     * @param expressionEvaluator 使用字段名与只读 measure Map 求值；输入尚未应用 orElse， 返回精确 Number 或正常
     *     null，计算异常直接传播
     * @return 完整指标字段结果；SCALAR 使用 value 字段，FIELD_SET 按字段名排序，容器不可修改
     */
    public Map<String, @Nullable Number> calculate(
            MetricDslSpec definition,
            Map<String, ? extends @Nullable Number> rawMeasures,
            BiFunction<String, Map<String, @Nullable Number>, ?> expressionEvaluator) {
        Map<String, MetricValueDsl> values = values(definition);
        Map<String, MetricValueDsl> measures = measures(definition);
        if (definition.fact() == null && !measures.isEmpty()) {
            throw invalid("/metric/value", "Derived metric must use expressions only");
        }
        requireFields(measures, rawMeasures);
        Map<String, @Nullable Number> normalized = new LinkedHashMap<>();
        measures.forEach(
                (field, value) -> {
                    @Nullable Number rawValue = rawMeasures.get(field);
                    if (value.measure().aggregation() == MetricAggregation.COUNT
                            && rawValue == null) {
                        throw invalid(
                                path(definition, field), "COUNT measure value must not be null");
                    }
                    normalized.put(field, normalize(value, rawValue, path(definition, field)));
                });
        Map<String, @Nullable Number> expressionInputs = Collections.unmodifiableMap(normalized);
        Map<String, @Nullable Number> result = new LinkedHashMap<>();
        values.forEach(
                (field, value) -> {
                    String path = path(definition, field);
                    @Nullable Number source = normalized.get(field);
                    if (value.expression() != null) {
                        @Nullable Object evaluated =
                                expressionEvaluator.apply(field, expressionInputs);
                        if (evaluated != null && !(evaluated instanceof Number)) {
                            throw invalid(
                                    path + "/expression",
                                    "Metric expression result must be numeric");
                        }
                        source = normalize(value, (Number) evaluated, path + "/expression");
                    }
                    result.put(field, source);
                });
        result.replaceAll(
                (field, source) -> applyOrElse(values.get(field), source, path(definition, field)));
        return Collections.unmodifiableMap(result);
    }

    private @Nullable Number applyOrElse(
            MetricValueDsl definition, @Nullable Number source, String path) {
        if (source != null) {
            return source;
        }
        @Nullable Number fallback =
                switch (definition.orElse().mode()) {
                    case NULL -> null;
                    case ZERO -> BigInteger.ZERO;
                    case VALUE ->
                            switch (definition.orElse().value()) {
                                case IntegralMetricLiteralDsl integral -> integral.value();
                                case DecimalMetricLiteralDsl decimal -> decimal.value();
                                case null -> throw invalid(path, "Metric orElse value is missing");
                            };
                };
        return normalize(definition, fallback, path);
    }

    private static @Nullable Number merge(
            MetricAggregation aggregation,
            @Nullable Number accumulated,
            @Nullable Number current,
            String path) {
        if (aggregation == MetricAggregation.COUNT) {
            if (current == null) {
                throw invalid(path, "COUNT segment value must not be null");
            }
            try {
                return ((BigInteger) accumulated)
                        .add(exactDecimal(current, path).toBigIntegerExact());
            } catch (ArithmeticException exception) {
                throw new MetricValidationException(
                        MetricErrorCode.RESULT_INVALID,
                        path,
                        "COUNT segment value must be an exact integer",
                        exception);
            }
        }
        if (current == null) {
            return accumulated;
        }
        BigDecimal decimal = exactDecimal(current, path);
        if (accumulated == null) {
            return decimal;
        }
        BigDecimal previous = (BigDecimal) accumulated;
        return switch (aggregation) {
            case SUM -> previous.add(decimal);
            case MIN -> previous.min(decimal);
            case MAX -> previous.max(decimal);
            case COUNT, AVG ->
                    throw new IllegalStateException("Aggregation must be validated before merging");
        };
    }

    private static BigDecimal exactDecimal(Number value, String path) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        throw invalid(path, "Metric value must use an exact numeric type");
    }

    private static Map<String, MetricValueDsl> values(MetricDslSpec definition) {
        return definition.valueShape() == MetricValueShape.SCALAR
                ? Map.of("value", definition.value())
                : new TreeMap<>(definition.fields());
    }

    private static Map<String, MetricValueDsl> measures(MetricDslSpec definition) {
        Map<String, MetricValueDsl> result = new LinkedHashMap<>();
        values(definition)
                .forEach(
                        (field, value) -> {
                            if (value.measure() != null) {
                                result.put(field, value);
                            }
                        });
        return result;
    }

    private static void requireFields(
            Map<String, MetricValueDsl> measures,
            @Nullable Map<String, ? extends @Nullable Number> values) {
        if (values == null || !measures.keySet().equals(values.keySet())) {
            throw invalid("/metric/values", "Metric measure fields do not match definition");
        }
    }

    private static String path(MetricDslSpec definition, String field) {
        return definition.valueShape() == MetricValueShape.SCALAR
                ? "/metric/value"
                : "/metric/fields/" + field.replace("~", "~0").replace("/", "~1");
    }

    private static MetricValidationException invalid(String path, String message) {
        return new MetricValidationException(MetricErrorCode.RESULT_INVALID, path, message);
    }
}
