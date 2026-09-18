package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MetricSqlTemplateRenderer} 的公开契约用例。
 *
 * <p>只观察 {@code renderSql} 的插值输出与异常，覆盖主体、参数、可选时间窗、数字格式与非法模板。
 * 渲染器使用 {@code ${...}} 直接插值，产出无参数绑定的 SQL 文本。</p>
 *
 * @author wuxp
 */
class MetricSqlTemplateRendererTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    private final MetricSqlTemplateRenderer renderer = new MetricSqlTemplateRenderer();

    @Test
    void rendersSubjectParameterAndWindowLiterals() {
        MetricSqlDefinition definition = definition(
                "SELECT SUM(`pay_amount`) AS total FROM `t_global_payment_income_detail`"
                        + " WHERE `tenant_id` = '${subjectId}' AND `pay_currency` = '${parameters['currency']}'"
                        + "<#if startTime?? && endTime??> AND `gmt_create` >= '${startTime}'"
                        + " AND `gmt_create` < '${endTime}'</#if>");

        assertEquals("SELECT SUM(`pay_amount`) AS total FROM `t_global_payment_income_detail`"
                + " WHERE `tenant_id` = 'tenant-1' AND `pay_currency` = 'USD'"
                + " AND `gmt_create` >= '2026-09-01 00:00:00' AND `gmt_create` < '2026-09-02 00:00:00'",
                renderer.renderSql(definition, query("tenant-1")));
    }

    @Test
    void omitsOptionalWindowWhenTimeIsAbsent() {
        MetricSqlDefinition definition = definition(
                "SELECT COUNT(*) FROM `t_vcc` WHERE `vcc_id` = '${subjectId}'"
                        + "<#if startTime??> AND `gmt_create` >= '${startTime}'</#if>");

        assertEquals("SELECT COUNT(*) FROM `t_vcc` WHERE `vcc_id` = 'vcc-1'",
                renderer.renderSql(definition, new MetricQuery("vcc-1", null, null, Map.of(), Map.of())));
    }

    @Test
    void rendersIntegersWithoutLocaleGrouping() {
        MetricSqlDefinition definition = definition("SELECT * FROM `t_vcc` LIMIT ${parameters['firstNPens']}");

        assertEquals("SELECT * FROM `t_vcc` LIMIT 2147483647",
                renderer.renderSql(definition, new MetricQuery(null, START, END, Map.of(), Map.of("firstNPens", 2147483647))));
    }

    @Test
    void rejectsInvalidTemplateSyntax() {
        MetricSqlDefinition definition = definition("SELECT ${unclosed");

        assertThrows(IllegalArgumentException.class, () -> renderer.renderSql(definition, query("tenant-1")));
    }

    private static MetricSqlDefinition definition(String sqlTemplate) {
        return new MetricSqlDefinition("metric_code", 1, MetricValueShape.SCALAR, "TENANT", List.of(), Map.of(), sqlTemplate);
    }

    private static MetricQuery query(String subjectId) {
        return new MetricQuery(subjectId, START, END, Map.of(), Map.of("currency", "USD"));
    }
}
