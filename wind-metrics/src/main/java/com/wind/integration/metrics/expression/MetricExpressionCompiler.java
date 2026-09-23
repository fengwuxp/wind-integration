package com.wind.integration.metrics.expression;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.definition.MetricExpressionDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;

import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.BooleanLiteral;
import org.springframework.expression.spel.ast.Elvis;
import org.springframework.expression.spel.ast.IntLiteral;
import org.springframework.expression.spel.ast.LongLiteral;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.NullLiteral;
import org.springframework.expression.spel.ast.OpAnd;
import org.springframework.expression.spel.ast.OpEQ;
import org.springframework.expression.spel.ast.OpGE;
import org.springframework.expression.spel.ast.OpGT;
import org.springframework.expression.spel.ast.OpLE;
import org.springframework.expression.spel.ast.OpLT;
import org.springframework.expression.spel.ast.OpMinus;
import org.springframework.expression.spel.ast.OpMultiply;
import org.springframework.expression.spel.ast.OpNE;
import org.springframework.expression.spel.ast.OpOr;
import org.springframework.expression.spel.ast.OpPlus;
import org.springframework.expression.spel.ast.OperatorNot;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.StringLiteral;
import org.springframework.expression.spel.ast.Ternary;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 将指标 SpEL 编译为受控 AST，并提取本地 measure 或跨指标依赖。
 *
 * <p>本类只做静态编译和结构校验：不加载指标、不选择 revision、不访问数据库，也不执行求值。
 * {@link MetricExpression} 承接已经编译的句柄，宿主提供值后再求值。</p>
 *
 * @author wuxp
 * @since 2026-07-23
 */
public final class MetricExpressionCompiler {

    private enum ExpressionMode {
        FACT,
        DERIVED
    }

    /** 单个指标表达式允许的最大字符数。 */
    static final int MAX_EXPRESSION_LENGTH = 2048;

    /** 单个指标表达式允许的最大 AST 深度。 */
    static final int MAX_AST_DEPTH = 32;

    private static final int MAX_METRIC_CODE_LENGTH = 100;

    private static final int MAX_VALUE_FIELD_LENGTH = 64;

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /**
     * 编译一条受限指标表达式，按所属定义的 measure 字段确定可引用范围。
     *
     * <p>调用方传入定义中所有 measure 字段名，无需选择事实或派生编译入口。集合非空时，
     * 表达式只可引用其中的字段，也允许常量；集合为空时，只可通过
     * {@code metric('CODE', 'FIELD')} 引用其他指标，且至少包含一个静态指标引用。
     * 该规则以合法事实定义至少包含一个 measure 为前提，不从表达式文本猜测指标类别。</p>
     *
     * <p>跨指标引用不携带 revision；精确版本由所属定义的 dependencies 声明，宿主按该声明
     * 准备依赖结果。编译器只提取和校验引用，不选择版本或加载数据。</p>
     *
     * @param definition 表达式定义
     * @param measureValueFields 当前定义允许引用的本地 measure 字段；传空集合表示派生表达式作用域
     * @param path 表达式字段路径
     * @return 已校验表达式及其本地字段/跨指标引用信息
     * @throws MetricValidationException 语法、作用域、类型、深度或安全边界不合法
     */
    public MetricExpression compile(MetricExpressionDsl definition, Set<String> measureValueFields, String path) {
        ExpressionMode mode = measureValueFields.isEmpty() ? ExpressionMode.DERIVED : ExpressionMode.FACT;
        if (definition.type() != MetricExpressionType.SPEL) {
            throw invalid(path + "/type", "Only SPEL expression is supported");
        }
        if (definition.value().length() > MAX_EXPRESSION_LENGTH) {
            throw invalid(path + "/value", "Metric expression exceeds maximum length");
        }
        SpelExpression expression;
        try {
            expression = (SpelExpression) PARSER.parseExpression(definition.value());
        } catch (ParseException exception) {
            throw new MetricValidationException(
                    MetricErrorCode.DSL_VALUE_INVALID,
                    path + "/value",
                    "Metric expression syntax is invalid",
                    exception);
        }
        Set<String> references = new LinkedHashSet<>();
        Set<MetricValueReference> metricValueReferences = new TreeSet<>();
        SpelNode root = expression.getAST();
        boolean ratio =
                validate(
                        root,
                        measureValueFields,
                        references,
                        metricValueReferences,
                        path + "/value",
                        true,
                        1,
                        mode);
        if (mode == ExpressionMode.DERIVED && metricValueReferences.isEmpty()) {
            throw invalid(path + "/value", "Derived expression must reference at least one metric value");
        }
        if (!isNumericResult(root)) {
            throw invalid(path + "/value", "Metric expression result must be numeric or null");
        }
        validateIntegralArithmetic(root, path + "/value");
        return new MetricExpression(expression, references, metricValueReferences, ratio);
    }

    private static boolean validate(
            SpelNode node,
            Set<String> measureValueFields,
            Set<String> references,
            Set<MetricValueReference> metricValueReferences,
            String path,
            boolean terminal,
            int depth,
            ExpressionMode mode) {
        if (depth > MAX_AST_DEPTH) {
            throw invalid(path, "Metric expression exceeds maximum AST depth");
        }
        if (node instanceof PropertyOrFieldReference reference) {
            if (!measureValueFields.contains(reference.getName())) {
                throw invalid(path, "Expression may only reference measure value fields");
            }
            references.add(reference.getName());
            return false;
        }
        if (node instanceof MethodReference method) {
            if (MetricExpressionRoot.METRIC_FUNCTION.equals(method.getName())) {
                validateMetricReference(method, metricValueReferences, path, mode);
                return false;
            }
            if (!terminal
                    || method.isNullSafe()
                    || !MetricExpressionRoot.RATIO_FUNCTION.equals(method.getName())
                    || method.getChildCount() != 2) {
                throw invalid(path, "Only terminal ratio(numerator, denominator) is supported");
            }
            for (int index = 0; index < method.getChildCount(); index++) {
                validate(
                        method.getChild(index),
                        measureValueFields,
                        references,
                        metricValueReferences,
                        path,
                        false,
                        depth + 1,
                        mode);
            }
            return true;
        }
        if (!isAllowedNode(node)) {
            throw invalid(path, "Expression contains an unsupported operation");
        }
        boolean ratio = false;
        for (int index = 0; index < node.getChildCount(); index++) {
            boolean childTerminal =
                    terminal && (node instanceof Elvis || node instanceof Ternary && index > 0);
            ratio |=
                    validate(
                            node.getChild(index),
                            measureValueFields,
                            references,
                            metricValueReferences,
                            path,
                            childTerminal,
                            depth + 1,
                            mode);
        }
        return ratio;
    }

    private static void validateMetricReference(
            MethodReference method,
            Set<MetricValueReference> references,
            String path,
            ExpressionMode mode) {
        if (mode != ExpressionMode.DERIVED
                || method.isNullSafe()
                || method.getChildCount() != 2
                || !(method.getChild(0) instanceof StringLiteral metricCodeLiteral)
                || !(method.getChild(1) instanceof StringLiteral valueFieldLiteral)) {
            throw invalid(
                    path, "metric requires two string literal arguments in a derived expression");
        }
        String metricCode = (String) metricCodeLiteral.getLiteralValue().getValue();
        String valueField = (String) valueFieldLiteral.getLiteralValue().getValue();
        if (!isIdentifier(metricCode, MAX_METRIC_CODE_LENGTH) || !isIdentifier(valueField, MAX_VALUE_FIELD_LENGTH)) {
            throw invalid(path, "metric arguments must use valid non-empty identifiers");
        }
        references.add(new MetricValueReference(metricCode, valueField));
    }

    private static boolean isIdentifier(String value, int maximumLength) {
        return value != null
                && value.length() <= maximumLength
                && IDENTIFIER.matcher(value).matches();
    }

    private static boolean isAllowedNode(SpelNode node) {
        return node instanceof NullLiteral
                || node instanceof BooleanLiteral
                || node instanceof IntLiteral
                || node instanceof LongLiteral
                || node instanceof OpPlus
                || node instanceof OpMinus
                || node instanceof OpMultiply
                || node instanceof OpEQ
                || node instanceof OpNE
                || node instanceof OpGT
                || node instanceof OpGE
                || node instanceof OpLT
                || node instanceof OpLE
                || node instanceof OpAnd
                || node instanceof OpOr
                || node instanceof OperatorNot
                || node instanceof Ternary
                || node instanceof Elvis;
    }

    private static boolean isNumericResult(SpelNode node) {
        if (node instanceof NullLiteral
                || node instanceof IntLiteral
                || node instanceof LongLiteral
                || node instanceof PropertyOrFieldReference
                || node instanceof MethodReference) {
            return true;
        }
        if (node instanceof Ternary) {
            return isNumericResult(node.getChild(1)) && isNumericResult(node.getChild(2));
        }
        if (node instanceof Elvis) {
            return isNumericResult(node.getChild(0)) && isNumericResult(node.getChild(1));
        }
        if (!(node instanceof OpPlus || node instanceof OpMinus || node instanceof OpMultiply)) {
            return false;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            if (!isNumericResult(node.getChild(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<IntegralRange> validateIntegralArithmetic(SpelNode node, String path) {
        if (node instanceof IntLiteral literal) {
            BigInteger value = BigInteger.valueOf((Integer) literal.getLiteralValue().getValue());
            return List.of(new IntegralRange(value, value, false));
        }
        if (node instanceof LongLiteral literal) {
            BigInteger value = BigInteger.valueOf((Long) literal.getLiteralValue().getValue());
            return List.of(new IntegralRange(value, value, true));
        }
        List<List<IntegralRange>> operands = new ArrayList<>(node.getChildCount());
        for (int index = 0; index < node.getChildCount(); index++) {
            operands.add(validateIntegralArithmetic(node.getChild(index), path));
        }
        if (node instanceof Ternary) {
            return mergeRanges(operands.get(1), operands.get(2));
        }
        if (node instanceof Elvis) {
            return mergeRanges(operands.get(0), operands.get(1));
        }
        if (!(node instanceof OpPlus || node instanceof OpMinus || node instanceof OpMultiply)) {
            return List.of();
        }
        if (operands.getFirst().isEmpty()) {
            return List.of();
        }
        List<IntegralRange> results = new ArrayList<>();
        if (operands.size() == 1) {
            for (IntegralRange operand : operands.getFirst()) {
                results.add(unaryRange(node, operand));
            }
        } else if (!operands.get(1).isEmpty()) {
            for (IntegralRange left : operands.getFirst()) {
                for (IntegralRange right : operands.get(1)) {
                    results.add(binaryRange(node, left, right));
                }
            }
        }
        List<IntegralRange> merged = mergeRanges(results);
        merged.forEach(range -> validateRange(range, path));
        return merged;
    }

    private static IntegralRange unaryRange(SpelNode node, IntegralRange operand) {
        if (node instanceof OpPlus) {
            return operand;
        }
        return new IntegralRange(
                operand.maximum().negate(), operand.minimum().negate(), operand.longType());
    }

    private static IntegralRange binaryRange(
            SpelNode node, IntegralRange left, IntegralRange right) {
        boolean longType = left.longType() || right.longType();
        if (node instanceof OpPlus) {
            return new IntegralRange(
                    left.minimum().add(right.minimum()),
                    left.maximum().add(right.maximum()),
                    longType);
        }
        if (node instanceof OpMinus) {
            return new IntegralRange(
                    left.minimum().subtract(right.maximum()),
                    left.maximum().subtract(right.minimum()),
                    longType);
        }
        List<BigInteger> products =
                List.of(
                        left.minimum().multiply(right.minimum()),
                        left.minimum().multiply(right.maximum()),
                        left.maximum().multiply(right.minimum()),
                        left.maximum().multiply(right.maximum()));
        return new IntegralRange(
                products.stream().min(BigInteger::compareTo).orElseThrow(),
                products.stream().max(BigInteger::compareTo).orElseThrow(),
                longType);
    }

    @SafeVarargs
    private static List<IntegralRange> mergeRanges(List<IntegralRange>... branches) {
        List<IntegralRange> ranges = new ArrayList<>();
        for (List<IntegralRange> branch : branches) {
            ranges.addAll(branch);
        }
        return mergeRanges(ranges);
    }

    private static List<IntegralRange> mergeRanges(List<IntegralRange> ranges) {
        List<IntegralRange> result = new ArrayList<>(2);
        mergeRangeType(ranges, false).ifPresent(result::add);
        mergeRangeType(ranges, true).ifPresent(result::add);
        return List.copyOf(result);
    }

    private static Optional<IntegralRange> mergeRangeType(
            List<IntegralRange> ranges, boolean longType) {
        return ranges.stream()
                .filter(range -> range.longType() == longType)
                .reduce(
                        (left, right) ->
                                new IntegralRange(
                                        left.minimum().min(right.minimum()),
                                        left.maximum().max(right.maximum()),
                                        longType));
    }

    private static void validateRange(IntegralRange range, String path) {
        boolean supported =
                range.longType()
                        ? fitsLong(range.minimum()) && fitsLong(range.maximum())
                        : fitsInt(range.minimum()) && fitsInt(range.maximum());
        if (!supported) {
            throw invalid(path, "Integral literal arithmetic exceeds its SpEL value range");
        }
    }

    private static boolean fitsInt(BigInteger value) {
        return value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                && value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0;
    }

    private static boolean fitsLong(BigInteger value) {
        return value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                && value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
    }

    private static MetricValidationException invalid(String path, String message) {
        return new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID, path, message);
    }

    /**
     * 整数常量子树在所有可达分支上的值域与 SpEL 原生结果类型。
     *
     * @param minimum 最小可能值
     * @param maximum 最大可能值
     * @param longType 是否按 long 计算
     */
    private record IntegralRange(BigInteger minimum, BigInteger maximum, boolean longType) {}
}
