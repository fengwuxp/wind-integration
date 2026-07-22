package com.wind.integration.metrics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * 新 DSL 引入后的旧指标 API 兼容合同测试。
 */
class WindMetricsLegacyCompatibilityTests {

    @Test
    void testAggregationQueryFactoriesKeepExistingContract() {
        LocalDateTime beginTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 7, 2, 0, 0);

        WindMetricsAggregationQuery query = WindMetricsAggregationQuery.newBuilder("customer", 1L)
                .queryVariable("currency", "CNY")
                .minGmtCreate(beginTime)
                .maxGmtCreate(endTime)
                .build();

        Assertions.assertEquals("customer", query.getDimensions());
        Assertions.assertEquals(1L, query.getDimensionsId());
        Assertions.assertEquals("CNY", query.getQueryVariables().get("currency"));
        Assertions.assertEquals(beginTime, query.getMinGmtCreate());
        Assertions.assertEquals(endTime, query.getMaxGmtCreate());
        Assertions.assertEquals("customer", WindMetricsAggregationQuery.of("customer", 1L).getDimensions());
    }

    @Test
    void testAggregatorFactoryKeepsBothFactoryMethods() throws NoSuchMethodException {
        Assertions.assertNotNull(WindMetricsAggregatorFactory.class.getMethod("factory", Class.class));
        Assertions.assertNotNull(WindMetricsAggregatorFactory.class.getMethod("factory", String.class, Class.class));
    }
}
