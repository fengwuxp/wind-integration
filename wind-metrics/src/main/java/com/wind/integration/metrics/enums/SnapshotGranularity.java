package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标快照桶的时间粒度。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
public enum SnapshotGranularity implements DescriptiveEnum {

    HOUR("小时"),
    DAY("自然日");

    /** 枚举描述。 */
    private final String desc;
}
