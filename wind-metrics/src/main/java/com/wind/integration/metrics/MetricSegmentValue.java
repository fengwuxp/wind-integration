package com.wind.integration.metrics;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * 指标分段值
 *
 * @author wuxp
 * @date 2026-09-22 21:50
 **/
public interface MetricSegmentValue<V> extends WindMetricsValue<V> {

    /**
     * 空表示从数据最初开始
     *
     * @return 分段范围开始时间，包含
     */
    @Nullable
    LocalDateTime startTime();

    /**
     * 空表示统计到当前时间
     *
     * @return 分段范围结束时间，不包含
     */
    @Nullable
    LocalDateTime endTime();
}
