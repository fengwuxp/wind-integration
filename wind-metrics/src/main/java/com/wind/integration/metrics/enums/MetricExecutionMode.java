package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标查询实际采用的数据执行模式。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum MetricExecutionMode implements DescriptiveEnum {

    REALTIME("实时聚合"),
    SNAPSHOT("快照查询"),
    SEGMENTED("分段查询");

    /** 枚举描述。 */
    private final String desc;
}
