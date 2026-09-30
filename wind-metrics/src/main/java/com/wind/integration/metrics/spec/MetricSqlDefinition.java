package com.wind.integration.metrics.spec;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricDefinitionType;
import com.wind.integration.metrics.enums.MetricErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 声明取得指标计算输入的 SQL 模板，值口径与精确依赖由 {@link MetricDefinition} 保存。
 *
 * <p>模板返回共同 value/fields 中非表达式字段的同名列。声明 measure 时，列值已是该聚合的
 * 原始状态；不再根据 field/filter 二次聚合。是否可合并、物化由状态能力决定，不由 SQL 名称决定。
 * 旧 SQL 没有值声明时仅按宿主原实时兼容路径读取，不猜测其类型、精度和累计能力。</p>
 *
 * @param sqlTemplate 受信 SQL 模板；渲染器负责模板语法，仓储负责实际执行
 * @author wuxp
 */
public record MetricSqlDefinition(String sqlTemplate) implements MetricValueQueryDefinition {

    public MetricSqlDefinition {
        Objects.requireNonNull(sqlTemplate, "sqlTemplate must not be null");
    }

    @Override
    public MetricDefinitionType type() {
        return MetricDefinitionType.SQL;
    }
    // 拒绝错层规则，避免保存时静默丢失共同口径或读取策略。
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
    @JsonAnySetter
    private void rejectUnknownProperty(String name, @Nullable Object ignored) {
        throw new MetricValidationException(MetricErrorCode.DSL_VALUE_INVALID,
                "/metric/valueQuery/" + name.replace("~", "~0").replace("/", "~1"), "Unknown value query property");
    }

}
