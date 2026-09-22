package com.wind.integration.metrics.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wind.integration.metrics.MetricSegmentValue;
import com.wind.integration.metrics.enums.MetricQueryMode;
import com.wind.integration.metrics.enums.MetricValueShape;
import com.wind.integration.metrics.json.MetricValuePayloadJsonDeserializer;
import com.wind.integration.metrics.json.MetricValuePayloadJsonSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 带指标身份、实际值和时间范围的查询结果，本身也是一个分段值。
 *
 * <p>value 是调用方提供的原始 payload，可为 null；fields 保留具名字段值，segmentValues 保留组成结果。
 * 本对象不执行计算、类型推断、集合复制或字段补齐，也不校验组成结果的层级和时间关系。
 * 时间边界遵循 {@link MetricSegmentValue}：空下界表示最初，空上界表示当前。</p>
 *
 * <p>JSON 的 value 按原始结构传输，小数保持精度；无类型 JSON 不恢复整数的原 Java 宽度，
 * 字符串不自动转换为日期。需要明确类型的值由 Runtime 返回值或 fields 中的具名值承接。
 * WindMetricsValue 的兼容访问器不额外产生 code、name 或 valueType 响应属性。</p>
 *
 * @param metricCode         对外查询的指标编码
 * @param definitionRevision 实际执行的指标定义修订号
 * @param executionMode      本次查询采用的顶层查询模式
 * @param valueShape         指标值结构
 * @param value              原始指标值，可为 null
 * @param fields             调用方装配的具名字段值
 * @param subjectId          主体标识；全局指标为空
 * @param startTime          实际统计范围开始时间，包含；全量快照为实际累计覆盖起点
 * @param endTime            实际统计范围结束时间，不包含；全量快照为实际累计覆盖末端
 * @param segmentValues      组成当前结果的分段值
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Builder
@JsonIgnoreProperties({"code", "name", "valueType"})
@JsonDeserialize(using = ValueDeserializer.None.class)
@Schema(description = "指标身份、原始值、时间范围及组成结果")
public record MetricResult(
        @Schema(description = "对外查询的指标编码") String metricCode,
        @Schema(description = "实际执行的指标定义修订号") Integer definitionRevision,
        @Nullable @Schema(description = "共同执行模式；混合来源为空") MetricQueryMode executionMode,
        @Schema(description = "指标值结构") MetricValueShape valueShape,
        @JsonSerialize(using = MetricValuePayloadJsonSerializer.class)
        @JsonDeserialize(using = MetricValuePayloadJsonDeserializer.class)
        @Schema(implementation = Object.class, description = "原始指标值，可为空") Object value,
        @Schema(description = "多字段指标结果；单值指标为空映射") Map<String, MetricFieldValue> fields,
        @Nullable @Schema(description = "主体标识；全局指标为空") String subjectId,
        @Nullable @Schema(description = "实际统计范围开始时间，包含；空表示最初") LocalDateTime startTime,
        @Nullable @Schema(description = "实际统计范围结束时间，不包含；空表示当前") LocalDateTime endTime,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Schema(description = "组成当前结果的分段值") List<MetricResult> segmentValues) implements MetricSegmentValue<Object> {

    @Override
    public String getName() {
        return getCode();
    }

    @Override
    public String getCode() {
        return metricCode;
    }

    @Override
    public @Nullable Object getValue() {
        return value;
    }
}
