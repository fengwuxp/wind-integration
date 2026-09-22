package com.wind.integration.tag;

import com.wind.integration.metrics.WindMetricsAggregationQuery;
import com.wind.jackson.WindJson;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 标签 JSON 的公共属性、查询投影与规则配置兼容；不代表宿主 DSL 支持标签过滤。
 *
 * @author wuxp
 * @since 2026-09-21
 */
@SuppressWarnings("deprecation")
class WindTagJsonTests {

    /**
     * 场景：业务实现 WindTag 接口而不是使用默认 record。
     * 输入：匿名实现的 region=CN。
     * 流程：WindJson 序列化后以 WindTag 还原。
     * 预期：JSON 恰好包含 name/value，不能输出空对象或丢失属性。
     */
    @Test
    void testCustomTagJsonRoundTrip() {
        WindTag tag = customTag();
        String json = WindJson.toJsonString(tag);

        assertEquals(Map.of("name", "region", "value", "CN"), jsonMap(json));
        WindTag restored = WindJson.parseObject(json, WindTag.class);
        assertEquals("region", restored.name());
        assertEquals("CN", restored.value());
    }

    /**
     * 场景：旧聚合 Query 仍暴露原模板属性，标签 JSON 同样使用两字段查询投影。
     * 输入：旧构造器接收 EntityTag 与自定义 WindTag。
     * 流程：序列化旧 Query，并单独按 WindTag 列表还原其中 searchTags。
     * 预期：历史六字段不变，标签不需要来源即可被规则/查询消费者解析。
     */
    @Test
    void testLegacyQueryKeepsTemplatePropertiesAndTagPairJson() {
        EntityTag entity = EntityTag.of("grade", "A", TagSource.MANUAL, "operator-1");
        WindMetricsAggregationQuery query = new WindMetricsAggregationQuery("USER", "customer",
                List.of(entity, customTag()), Map.of(), null, null);
        Map<?, ?> json = jsonMap(WindJson.toJsonString(query));

        assertEquals(Set.of("dimensions", "dimensionsId", "searchTags", "queryVariables", "minGmtCreate", "maxGmtCreate"),
                json.keySet());
        assertEquals(List.of(Map.of("name", "grade", "value", "A"), Map.of("name", "region", "value", "CN")),
                json.get("searchTags"));
        List<WindTag> tags = WindJson.parseObject(WindJson.toJsonString(json.get("searchTags")), new TypeReference<>() {
        });
        assertEquals(Map.of("grade", Set.of("A"), "region", Set.of("CN")), WindTag.groupTagsKeyValues(tags));
        assertSame(entity, query.getSearchTags().iterator().next());
    }

    /**
     * 场景：标签用于实际打标时仍需要来源和来源标识。
     * 输入：独立 EntityTag 的 MANUAL 来源及字符串来源标识。
     * 流程：按 EntityTag 本身序列化和还原。
     * 预期：四个属性完整保留，查询投影不影响独立业务对象。
     */
    @Test
    void testEntityTagRetainsStandaloneProvenance() {
        EntityTag entity = EntityTag.of("grade", "A", TagSource.MANUAL, "operator-1");
        String json = WindJson.toJsonString(entity);

        assertEquals(Set.of("name", "value", "source", "sourceId"), jsonMap(json).keySet());
        assertEquals(entity, WindJson.parseObject(json, EntityTag.class));
    }

    /**
     * 场景：Capte 与 nobe 规则配置先解析无来源标签，再由打标方绑定规则身份。
     * 输入：两个同名不同值的规则配置标签。
     * 流程：按 List<WindTag> 读取，再转换成 EntityTag 列表。
     * 预期：两个值都保留，转换后来源来自规则调用，不要求配置携带来源。
     */
    @Test
    void testRuleConfigurationTagsCanAcquireProvenanceAfterParsing() {
        List<WindTag> tags = WindJson.parseObject(
                "[{\"name\":\"risk\",\"value\":\"high\"},{\"name\":\"risk\",\"value\":\"review\"}]",
                new TypeReference<>() {
                });
        List<EntityTag> sourced = EntityTag.tags(TagSource.RULE_BASED, "rule-12", tags);

        assertEquals(Set.of("high", "review"), WindTag.getTagValues(sourced));
        for (EntityTag tag : sourced) {
            assertEquals(TagSource.RULE_BASED, tag.source());
            assertEquals("rule-12", tag.sourceId());
        }
    }

    /**
     * 场景：Java 工厂与 JSON 入口保留既有空字符串语义。
     * 输入：名称和值均为空字符串的标签。
     * 流程：调用公共 of/tags 工厂并进行 JSON 往返。
     * 预期：单个和列表工厂结果一致，空字符串原样保留。
     */
    @Test
    void testFactoriesKeepExistingEmptyStringValues() {
        WindTag empty = WindTag.of("", "");
        assertEquals(List.of(empty), WindTag.tags("", ""));
        assertEquals(empty, WindJson.parseObject(WindJson.toJsonString(empty), WindTag.class));
    }

    @NullMarked
    private static WindTag customTag() {
        return new WindTag() {
            @Override
            public String name() {
                return "region";
            }

            @Override
            public String value() {
                return "CN";
            }
        };
    }

    private static Map<?, ?> jsonMap(String json) {
        return WindJson.getJsonMapper().readValue(json, Map.class);
    }
}
