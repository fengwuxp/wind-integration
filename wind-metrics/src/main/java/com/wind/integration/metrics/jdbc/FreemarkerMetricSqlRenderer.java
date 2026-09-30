package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDefinition;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 {@link MetricSqlDefinition} 的 Freemarker SQL 模板渲染为可直接执行的 SQL 文本。
 *
 * <p>与 {@link MetricJdbcSqlCompiler}（DSL 模式产出参数化 SQL 与有序绑定）不同，本渲染器按
 * 内部配置信任模型使用 Freemarker {@code ${...}} 直接插值：查询值以字面量进入 SQL 文本，不产生
 * 参数绑定。模板来自已通过宿主校验的受信配置，取值来自本次 {@link MetricQuery}；
 * 调用方必须约束运行时输入，字符串字面量的引号与转义由模板作者负责。模板校验本身
 * 不提供运行时值的转义；需要安全绑定外部查询值时应使用参数化 SQL 能力。</p>
 *
 * <p>模板可引用以下数据模型变量：</p>
 * <ul>
 *   <li>{@code subjectId} —— 单主体标识，来自 {@link MetricQuery#subjectId()}</li>
 *   <li>{@code subjectType} —— 主体类型，优先 {@link MetricQuery#subjectType()}，缺省用定义值</li>
 *   <li>{@code dimensions} —— 具名维度值 Map，来自 {@link MetricQuery#dimensionValues()}</li>
 *   <li>{@code parameters} —— 具名参数值 Map（旧模板的 {@code queryVariables} 对应此处）</li>
 *   <li>{@code startTime} / {@code endTime} —— 半开窗端点，整秒格式为 {@code yyyy-MM-dd HH:mm:ss}，
 *       非整秒保留实际小数秒（最多9位），缺省为 null；配合 {@code <#if startTime??>} 表达可选时间窗</li>
 * </ul>
 *
 * <p>数字按 {@code computer} 格式渲染，避免按 Locale 分组（如 {@code 2,147,483,647}）破坏 SQL。
 * 模板语法或渲染异常统一包装为 {@link IllegalArgumentException}。</p>
 *
 * @author wuxp
 */
public final class FreemarkerMetricSqlRenderer implements MetricSqlGenerator {

    private static final DateTimeFormatter SQL_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter();

    private final Configuration configuration;

    public FreemarkerMetricSqlRenderer() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setNumberFormat("computer");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        this.configuration = configuration;
    }

    /**
     * 实现 {@link MetricSqlGenerator}，按 SQL 模板模式生成查询。
     *
     * @param definition 共同定义，其取值声明必须为 {@link MetricSqlDefinition}
     * @param query 查询条件
     * @return 插值后的 SQL、声明字段的同名投影；无参数绑定
     * @throws IllegalArgumentException 定义不是 SQL 形态或模板渲染失败
     */
    @Override
    public MetricSqlDescriptor generate(MetricDefinition definition, MetricQuery query) {
        Map<String, String> projections = new LinkedHashMap<>();
        if (definition.value() != null && definition.value().expression() == null) {
            projections.put("value", "value");
        }
        definition.fields().forEach((field, value) -> {
            if (value.expression() == null) {
                projections.put(field, field);
            }
        });
        return new MetricSqlDescriptor(renderSql(definition, query), List.of(), projections);
    }

    /**
     * 用一次查询条件渲染指标 SQL 模板。
     *
     * @param definition SQL 模板指标定义，不执行 IO
     * @param query 当前查询条件；时间端点可空，由模板自行决定是否拼接
     * @return 插值后的 SQL 文本
     * @throws IllegalArgumentException 模板语法错误或渲染失败
     */
    public String renderSql(MetricDefinition definition, MetricQuery query) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(query, "query must not be null");
        StringWriter out = new StringWriter();
        try {
            template(definition).process(dataModel(definition, query), out);
        } catch (IOException | TemplateException exception) {
            throw new IllegalArgumentException("Failed to render SQL template for metric " + definition.code(), exception);
        }
        return out.toString();
    }

    private Template template(MetricDefinition definition) {
        try {
            return new Template(definition.code(), new StringReader(sql(definition).sqlTemplate()), configuration);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid SQL template for metric " + definition.code(), exception);
        }
    }

    private static MetricSqlDefinition sql(MetricDefinition definition) {
        if (definition.valueQuery() instanceof MetricSqlDefinition sql) {
            return sql;
        }
        throw new IllegalArgumentException("SQL renderer requires a SQL value query");
    }

    private static Map<String, Object> dataModel(MetricDefinition definition, MetricQuery query) {
        Map<String, Object> model = new HashMap<>();
        model.put("subjectId", query.subjectId());
        model.put("subjectType", query.subjectType() == null ? definition.subjectType() : query.subjectType());
        model.put("dimensions", query.dimensionValues() == null ? Map.of() : query.dimensionValues());
        model.put("parameters", query.parameterValues() == null ? Map.of() : query.parameterValues());
        model.put("startTime", format(query.startTime()));
        model.put("endTime", format(query.endTime()));
        return model;
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : SQL_TIMESTAMP.format(value);
    }
}
