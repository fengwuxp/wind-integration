package com.wind.integration.metrics;

import com.wind.common.enums.DescriptiveEnum;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricDefinitionType;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricJoinCardinality;
import com.wind.integration.metrics.enums.MetricJoinType;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.MetricDerivationType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import com.wind.jackson.WindJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 指标公共枚举的 Wind 约规契约测试。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
class MetricEnumContractTests {

    /**
     * 场景：公共枚举为调用方提供可读说明。
     * 输入：当前列出的15类指标枚举及各自全部值。
     * 流程：逐值检查 DescriptiveEnum 契约与描述。
     * 预期：每项实现说明接口且描述非空白。
     */
    @Test
    void testMetricEnumsProvideNonBlankDescriptions() {
        List<Class<? extends Enum<?>>> enumTypes = List.of(
                MetricAggregation.class,
                MetricDefinitionType.class,
                MetricErrorCode.class,
                MetricQueryMode.class,
                MetricExpressionType.class,
                MetricFilterOperator.class,
                MetricJoinCardinality.class,
                MetricJoinType.class,
                MetricOrElseMode.class,
                MetricSegmentCode.class,
                MetricSegmentSourceType.class,
                MetricValueShape.class,
                MetricValueType.class,
                MetricDerivationType.class,
                MetricSnapshotGranularity.class);

        enumTypes.forEach(enumType -> {
            for (Enum<?> enumValue : enumType.getEnumConstants()) {
                DescriptiveEnum descriptiveEnum = Assertions.assertInstanceOf(DescriptiveEnum.class, enumValue);
                Assertions.assertFalse(descriptiveEnum.getDesc().isBlank(), enumValue.name());
            }
        });
    }

    /**
     * 场景：查询模式及中文说明保持稳定。
     * 输入：MetricQueryMode 全部值。
     * 流程：读取枚举顺序和各模式描述。
     * 预期：只有 REALTIME、SNAPSHOT、SEGMENTED，分别表示实时、快照、分段查询。
     */
    @Test
    void testMetricQueryModeContract() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(MetricQueryMode.REALTIME, MetricQueryMode.SNAPSHOT, MetricQueryMode.SEGMENTED),
                        List.of(MetricQueryMode.values())),
                () -> Assertions.assertEquals("实时查询", MetricQueryMode.REALTIME.getDesc()),
                () -> Assertions.assertEquals("快照查询", MetricQueryMode.SNAPSHOT.getDesc()),
                () -> Assertions.assertEquals("分段查询", MetricQueryMode.SEGMENTED.getDesc()));
    }

    /**
     * 场景：分段标识保留小写 JSON 协议编码。
     * 输入：ARCHIVE 枚举及 JSON 字符串 recent。
     * 流程：分别序列化和反序列化。
     * 预期：输出 archive，读取结果为 RECENT。
     */
    @Test
    void testMetricSegmentCodeUsesStableJsonCode() {
        Assertions.assertAll(
                () -> Assertions.assertEquals("\"archive\"", WindJson.toJsonString(MetricSegmentCode.ARCHIVE)),
                () -> Assertions.assertEquals(
                        MetricSegmentCode.RECENT,
                        WindJson.parseObject("\"recent\"", MetricSegmentCode.class)));
    }

    /**
     * 场景：Capte 历史定义类型迁入公共枚举后仍可原样读写。
     * 输入：SQL、EXPRESSION、SCRIPT、FUNCTION、DSL 的 JSON 字符串。
     * 流程：逐项反序列化再序列化。
     * 预期：枚举名和 JSON 编码均与输入一致。
     */
    @Test
    void testDefinitionTypePreservesLegacyJsonCodes() {
        for (String code : List.of("SQL", "EXPRESSION", "SCRIPT", "FUNCTION", "DSL")) {
            String json = "\"" + code + "\"";
            MetricDefinitionType type = WindJson.parseObject(json, MetricDefinitionType.class);

            Assertions.assertEquals(code, type.name());
            Assertions.assertEquals(json, WindJson.toJsonString(type));
        }
    }

    /**
     * 场景：派生分类迁入 Wind 后保持历史编码和分类行为。
     * 输入：RAW 与 DERIVED 的 JSON 字符串。
     * 流程：解析、调用 isDerived，再写回 JSON。
     * 预期：RAW 为 false、DERIVED 为 true，两者编码原样保留。
     */
    @Test
    void testDerivationTypePreservesLegacyJsonContract() {
        MetricDerivationType raw = WindJson.parseObject("\"RAW\"", MetricDerivationType.class);
        MetricDerivationType derived = WindJson.parseObject("\"DERIVED\"", MetricDerivationType.class);

        Assertions.assertAll(
                () -> Assertions.assertFalse(raw.isDerived()),
                () -> Assertions.assertTrue(derived.isDerived()),
                () -> Assertions.assertEquals("\"RAW\"", WindJson.toJsonString(raw)),
                () -> Assertions.assertEquals("\"DERIVED\"", WindJson.toJsonString(derived)));
    }
}
