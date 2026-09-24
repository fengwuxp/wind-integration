package com.wind.integration.metrics;

import com.wind.integration.metrics.query.MetricQuery;
import com.wind.integration.tag.WindTag;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 旧查询到通用条件的值保持、旧求值入口和模板属性兼容。 */
@SuppressWarnings("deprecation")
class WindMetricsCriteriaCompatibilityTests {

    /**
     * 场景：旧条件转为公共条件后可无损返回。
     * 输入：USER 主体集合11/12、无标签、SETTLED 参数及运行时对象。
     * 流程：legacy.asQuery 后再 fromQuery。
     * 预期：主体仍为集合、独立维度为空、上下文对象身份保留，时间为空且原查询相等。
     */
    @Test
    void testLegacyConditionsRoundTripWithoutInventingDimensionsOrConvertingSubjects() {
        Object context = new Object();
        WindMetricsAggregationQuery legacy = WindMetricsAggregationQuery.newBuilder("USER", Set.of(11L, 12L))
                .queryVariable(Map.of("runtime", context, "state", "SETTLED")).build();
        MetricQuery criteria = legacy.asQuery();

        assertEquals(Set.of(11L, 12L), criteria.subjectId());
        assertEquals(Map.of(), criteria.dimensionValues());
        assertSame(context, criteria.parameterValues().get("runtime"));
        assertEquals(legacy, WindMetricsAggregationQuery.fromQuery(criteria));
        assertNull(criteria.startTime());
        assertNull(criteria.endTime());
    }

    /**
     * 场景：兼容旧查询允许空属性与可变 getter 的行为。
     * 输入：全 null 旧查询，及带可变变量/标签集合的 USER 查询。
     * 流程：往返空查询，再经 getter 添加 state 和 currency 标签。
     * 预期：空属性和变量保留；旧标签仍可读取，但有标签后转换公共条件会明确拒绝。
     */
    @Test
    void testLegacyNullValuesAndGetterMutabilityRemainAvailable() {
        WindMetricsAggregationQuery empty = new WindMetricsAggregationQuery(null, null, null, null, null, null);
        WindMetricsAggregationQuery restored = WindMetricsAggregationQuery.fromQuery(empty.asQuery());
        assertEquals(empty.asQuery(), restored.asQuery());
        assertEquals(Set.of(), restored.getSearchTags());
        Map<String, Object> variables = new HashMap<>();
        WindMetricsAggregationQuery legacy = new WindMetricsAggregationQuery("USER", 1L,
                new HashSet<>(), variables, null, null);
        legacy.getQueryVariables().put("state", "SETTLED");
        assertEquals("SETTLED", legacy.asQuery().parameterValues().get("state"));
        legacy.getSearchTags().add(WindTag.of("currency", "USD"));
        assertEquals(Set.of(WindTag.of("currency", "USD")), legacy.getSearchTags());
        assertThrows(IllegalArgumentException.class, legacy::asQuery);
    }

    /**
     * 场景：旧标签查询仍可由旧求值器使用，不能在迁移时丢弃过滤条件。
     * 输入：currency=USD 标签查询与读取该标签的旧求值器。
     * 流程：执行旧入口，再尝试转换为 MetricQuery。
     * 预期：旧入口读到USD；转换明确失败且原始标签不变。
     */
    @Test
    void testTaggedLegacyQueryRemainsUsableButCannotConvertToMetricQuery() {
        WindMetricsAggregationQuery legacy = WindMetricsAggregationQuery.newBuilder("USER", "customer")
                .tag("currency", "USD").build();
        WindMetricsEvaluator<String> evaluator = query -> query.getSearchTags().iterator().next().value();

        assertEquals("USD", evaluator.evaluate(legacy));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, legacy::asQuery);
        assertEquals("MetricQuery cannot represent legacy searchTags; use the legacy query entry", failure.getMessage());
        assertEquals(Set.of(WindTag.of("currency", "USD")), legacy.getSearchTags());
    }

    /**
     * 场景：公共条件适配旧求值入口时保留运行时对象。
     * 输入：主体列表1/2及 runtime 对象，另传 null 查询。
     * 流程：显式转换公共条件后调用旧 evaluate。
     * 预期：返回同一 runtime 实例，null 入口仍返回 null。
     */
    @Test
    void testCommonEvaluatorDelegatesWithoutSerializingRuntimeContext() {
        Object context = new Object();
        MetricQuery criteria = new MetricQuery(List.of(1L, 2L), "USER", null, null,
                Map.of(), Map.of("runtime", context));
        WindMetricsEvaluator<Object> evaluator = query -> query == null ? null : query.getQueryVariables().get("runtime");

        assertSame(context, evaluator.evaluate(WindMetricsAggregationQuery.fromQuery(criteria)));
        assertNull(evaluator.evaluate(WindMetricsAggregationQuery.fromQuery(null)));
        assertNull(evaluator.evaluate(null));
    }

    /**
     * 场景：旧入口不能表达独立维度时应明确拒绝。
     * 输入：维度 currency=USD，参数同名 currency=EUR。
     * 流程：显式转换条件后调用旧 evaluator。
     * 预期：抛参数异常且不进入求值，避免丢弃或合并两个命名空间。
     */
    @Test
    void testLegacyProjectionRejectsIndependentDimensionsWithoutDroppingOrMergingThem() {
        MetricQuery criteria = new MetricQuery("1", null, null,
                Map.of("currency", "USD"), Map.of("currency", "EUR"));
        WindMetricsEvaluator<Object> evaluator = query -> {
            throw new AssertionError("Invalid projection must fail before evaluation");
        };
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(WindMetricsAggregationQuery.fromQuery(criteria)));
    }


    /**
     * 场景：旧模板查询 JSON 属性集合保持兼容。
     * 输入：USER、主体1的旧查询。
     * 流程：序列化并读取字段集合。
     * 预期：只含历史6个模板属性，不因公共条件适配增加属性。
     */
    @Test
    void testLegacyJsonStillContainsOnlyTheSixTemplateProperties() {
        WindMetricsAggregationQuery legacy = WindMetricsAggregationQuery.of("USER", 1L);
        Map<?, ?> json = WindJson.getJsonMapper().readValue(WindJson.toJsonString(legacy), Map.class);
        assertEquals(Set.of("dimensions", "dimensionsId", "searchTags", "queryVariables", "minGmtCreate", "maxGmtCreate"), json.keySet());
    }
}
