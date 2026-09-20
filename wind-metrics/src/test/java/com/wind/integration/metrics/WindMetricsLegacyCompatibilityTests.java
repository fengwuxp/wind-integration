package com.wind.integration.metrics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * 新 DSL 引入后的旧指标 API 兼容合同测试。
 */
class WindMetricsLegacyCompatibilityTests {

    /**
     * 场景：引入 DSL 后旧查询工厂仍保留原调用语义。
     * 输入：customer=1、currency=CNY、2026-07-01至07-02。
     * 流程：使用 builder 构造并另调用 of 工厂。
     * 预期：主体、参数、起止时间均保留，of 工厂仍提供 customer 维度。
     */
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

    /**
     * 场景：旧聚合器工厂的两种签名继续可用。
     * 输入：WindMetricsAggregatorFactory 公共类型。
     * 流程：反射查找 factory(Class) 和 factory(String, Class)。
     * 预期：两个方法都存在；此用例验证签名兼容，不执行聚合。
     */
    @Test
    void testAggregatorFactoryKeepsBothFactoryMethods() throws NoSuchMethodException {
        Assertions.assertNotNull(WindMetricsAggregatorFactory.class.getMethod("factory", Class.class));
        Assertions.assertNotNull(WindMetricsAggregatorFactory.class.getMethod("factory", String.class, Class.class));
    }
}
