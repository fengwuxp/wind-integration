package com.wind.integration.metrics.dsl.materialization;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.enums.MetricErrorCode;
import com.wind.integration.metrics.enums.MetricSegmentSourceType;
import com.wind.integration.metrics.enums.MetricSnapshotGranularity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 物化计划中的时间范围规则，决定哪些左闭右开范围需要快照，不重复计算口径或查询路由。
 *
 * <p>近期实时段使用 {@code window}，如 {@code P90D}；历史快照段省略时间边界，
 * 承接该窗口之前的全部历史。固定范围使用 {@code start}/{@code end}，不与窗口混用。
 * {@code snapshotGranularity} 为空表示整体快照，{@code YEAR} 表示按自然年组织快照；
 * 首尾不足一个周期时，物化和读取均须保存并校验实际覆盖范围，不能裁切整周期聚合值。</p>
 *
 * <p>配置近到远，由物化执行器展开范围、读取已有进度并统计待补差额。
 * 查询只按业务键读取已提交快照，按实际时间合并，不解释这些规则；需要实时尾部时，
 * 起点由实际数据的连续末端确定。固定物化范围不得自动移动边界，也不覆盖查询条件。
 * 本对象只校验声明，不读取数据、不求值表达式、不生成内部快照身份。</p>
 *
 * @param sourceType          分段数据来源
 * @param window              近期实时窗口，PnD 按宿主时区的自然日、PTnH 按经过小时解释；n 为正整数
 * @param start               固定范围起点，包含；仅最远固定段可为空，表示无下界
 * @param end                 固定范围终点，不包含；滚动规则省略
 * @param snapshotGranularity 快照组织的时间粒度；为空表示整体一段，实时来源必须为空
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "物化计划的时间分段规则，不包含段编码或独立保存目标")
public record MetricSegmentDsl(
        @JsonProperty(required = true) @Schema(description = "分段数据来源") MetricSegmentSourceType sourceType,
        @Nullable @Schema(description = "近期实时窗口，例如 P90D；与固定边界互斥") String window,
        @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
        @JsonSerialize(using = LocalDateTimeSerializer.class) @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        @Nullable @Schema(description = "固定范围左闭起点；最远段可为空表示无下界") LocalDateTime start,
        @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
        @JsonSerialize(using = LocalDateTimeSerializer.class) @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        @Nullable @Schema(description = "固定范围右开终点；滚动规则省略") LocalDateTime end,
        @Nullable @Schema(description = "快照时间粒度；为空表示整体一段") MetricSnapshotGranularity snapshotGranularity) {

    private static final Pattern WINDOW_PATTERN = Pattern.compile("P[1-9]\\d*D|PT[1-9]\\d*H");

    public MetricSegmentDsl {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        if (sourceType == MetricSegmentSourceType.REALTIME && snapshotGranularity != null) {
            throw invalid("/snapshotGranularity", "REALTIME segments must not declare snapshot granularity");
        }
        if (window != null) {
            if (sourceType != MetricSegmentSourceType.REALTIME || start != null || end != null) {
                throw invalid("/window", "Only REALTIME may declare a window, without fixed boundaries");
            }
            validateWindow(window);
        } else if (end != null) {
            if (start != null && !start.isBefore(end)) {
                throw invalid("/start", "Fixed segment start must precede its exclusive end");
            }
        } else if (start != null || sourceType == MetricSegmentSourceType.REALTIME) {
            throw invalid("/end", "Fixed segments require an end; REALTIME requires a window or fixed range");
        }
    }

    // Jackson 反射调用；旧字段必须报错，不能被 WindJson 的默认宽松解析静默丢弃。
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
    @JsonAnySetter
    private void rejectUnknownProperty(String name, @Nullable Object value) {
        throw invalid("/" + name.replace("~", "~0").replace("/", "~1"), "Unknown segment property");
    }

    // 解析用于拒绝 Duration 数值溢出，不需要保留计算结果。
    @SuppressWarnings("PMD.UselessPureMethodCall")
    private static void validateWindow(String window) {
        if (!WINDOW_PATTERN.matcher(window).matches()) {
            throw invalid("/window", "Window must be a positive whole-day or whole-hour duration, such as P90D or PT24H");
        }
        try {
            Duration.parse(window);
        } catch (DateTimeParseException | ArithmeticException exception) {
            throw new MetricValidationException(MetricErrorCode.DSL_PLAN_INVALID, "/window", "Window duration is out of range", exception);
        }
    }

    private static MetricValidationException invalid(String path, String message) {
        return new MetricValidationException(MetricErrorCode.DSL_PLAN_INVALID, path, message);
    }
}
