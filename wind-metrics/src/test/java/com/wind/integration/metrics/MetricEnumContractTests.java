package com.wind.integration.metrics;

import com.wind.common.enums.DescriptiveEnum;
import com.wind.integration.metrics.enums.MetricAggregation;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricExpressionType;
import com.wind.integration.metrics.enums.MetricFilterOperator;
import com.wind.integration.metrics.enums.MetricJoinCardinality;
import com.wind.integration.metrics.enums.MetricJoinType;
import com.wind.integration.metrics.enums.MetricMergeState;
import com.wind.integration.metrics.enums.MetricOrElseMode;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricSegmentCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.enums.MetricValueType;
import com.wind.integration.metrics.enums.SnapshotGranularity;
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

    @Test
    void testMetricEnumsProvideNonBlankDescriptions() {
        List<Class<? extends Enum<?>>> enumTypes = List.of(
                MetricAggregation.class,
                MetricErrorCode.class,
                MetricQueryMode.class,
                MetricExpressionType.class,
                MetricFilterOperator.class,
                MetricJoinCardinality.class,
                MetricJoinType.class,
                MetricMergeState.class,
                MetricOrElseMode.class,
                MetricSegmentCode.class,
                MetricSegmentSourceType.class,
                MetricValueShape.class,
                MetricValueType.class,
                SnapshotGranularity.class);

        enumTypes.forEach(enumType -> {
            for (Enum<?> enumValue : enumType.getEnumConstants()) {
                DescriptiveEnum descriptiveEnum = Assertions.assertInstanceOf(DescriptiveEnum.class, enumValue);
                Assertions.assertFalse(descriptiveEnum.getDesc().isBlank(), enumValue.name());
            }
        });
    }

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
}
