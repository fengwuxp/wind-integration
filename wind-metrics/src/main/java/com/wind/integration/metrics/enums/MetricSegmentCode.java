package com.wind.integration.metrics.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分段物化计划中固定的时间分段标识。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "分段物化计划中固定的时间分段标识")
public enum MetricSegmentCode implements DescriptiveEnum {

    @Schema(description = "历史分段")
    ARCHIVE("archive", "历史分段"),
    @Schema(description = "近期分段")
    RECENT("recent", "近期分段");

    /** 稳定协议编码。 */
    private final String code;

    /** 枚举描述。 */
    private final String desc;

    /**
     * 返回稳定协议编码。
     *
     * @return {@code archive} 或 {@code recent}
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 按稳定协议编码解析分段。
     *
     * @param code 稳定协议编码
     * @return 分段枚举
     * @throws IllegalArgumentException 编码不受支持时抛出
     */
    @JsonCreator
    public static MetricSegmentCode fromCode(String code) {
        for (MetricSegmentCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported metric segment code: " + code);
    }
}
