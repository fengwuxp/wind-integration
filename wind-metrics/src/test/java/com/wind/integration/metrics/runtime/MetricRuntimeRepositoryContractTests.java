package com.wind.integration.metrics.runtime;

import com.wind.integration.metrics.WindMetricsValue;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.metrics.spec.MetricDefinitionObject;
import com.wind.integration.metrics.spec.MetricSqlDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 验证实时仓储由实现方选择完整返回对象类型，Wind 不把字段 Map 固定进公共接口。
 */
class MetricRuntimeRepositoryContractTests {

    private static final MetricDefinitionObject DEFINITION = new MetricSqlDefinition(
            "ORDER_COUNT", 1, MetricValueShape.SCALAR, "GLOBAL", List.of(), Map.of(),
            "SELECT COUNT(*) FROM t_order");

    private static final MetricQuery QUERY = new MetricQuery("subject-1", null, null, Map.of(), Map.of());

    @Test
    void testRepositoryCanReturnHostSpecificObject() {
        RuntimeValue expected = new RuntimeValue("ORDER_COUNT", 3L);
        MetricRuntimeRepository<RuntimeValue> repository = (definition, query) -> expected;

        Assertions.assertSame(expected, repository.query(DEFINITION, QUERY));
    }

    @Test
    void testMapRemainsAValidHostSpecificRuntimeObject() {
        Map<String, WindMetricsValue<?>> expected = Map.of("value", WindMetricsValue.of("value", 3L));
        MetricRuntimeRepository<Map<String, WindMetricsValue<?>>> repository = (definition, query) -> expected;

        Assertions.assertSame(expected, repository.query(DEFINITION, QUERY));
    }

    private record RuntimeValue(String code, Long value) {
    }
}
