package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标定义、查询和结果契约的稳定校验错误码。
 *
 * @author wuxp
 * @date 2026-07-21 17:51
 */
@Getter
@AllArgsConstructor
@Schema(description = "指标定义、查询和结果契约的稳定校验错误码")
public enum MetricErrorCode implements DescriptiveEnum {

    @Schema(description = "DSL JSON 格式无效")
    DSL_JSON_INVALID("DSL JSON 格式无效"),
    @Schema(description = "DSL 根节点不是对象")
    DSL_ROOT_NOT_OBJECT("DSL 根节点不是对象"),
    @Schema(description = "DSL 字段重复")
    DSL_FIELD_DUPLICATED("DSL 字段重复"),
    @Schema(description = "DSL 字段未知")
    DSL_FIELD_UNKNOWN("DSL 字段未知"),
    @Schema(description = "DSL 必填字段缺失")
    DSL_FIELD_REQUIRED("DSL 必填字段缺失"),
    @Schema(description = "DSL 字段类型无效")
    DSL_FIELD_TYPE_INVALID("DSL 字段类型无效"),
    @Schema(description = "DSL 版本不受支持")
    DSL_SCHEMA_VERSION_UNSUPPORTED("DSL 版本不受支持"),
    @Schema(description = "DSL 标识符无效")
    DSL_IDENTIFIER_INVALID("DSL 标识符无效"),
    @Schema(description = "DSL 指标值分支无效")
    DSL_VALUE_BRANCH_INVALID("DSL 指标值分支无效"),
    @Schema(description = "DSL 指标值无效")
    DSL_VALUE_INVALID("DSL 指标值无效"),
    @Schema(description = "DSL 物化计划无效")
    DSL_PLAN_INVALID("DSL 物化计划无效"),
    @Schema(description = "指标查询参数缺失")
    METRIC_PARAMETER_MISSING("指标查询参数缺失"),
    @Schema(description = "指标查询参数未声明")
    METRIC_PARAMETER_UNEXPECTED("指标查询参数未声明"),
    @Schema(description = "指标查询参数类型不匹配")
    METRIC_PARAMETER_TYPE_MISMATCH("指标查询参数类型不匹配"),
    @Schema(description = "指标查询参数超出范围")
    METRIC_PARAMETER_OUT_OF_RANGE("指标查询参数超出范围"),
    @Schema(description = "指标定义参数未使用")
    METRIC_PARAMETER_UNUSED("指标定义参数未使用"),
    @Schema(description = "指标行选择定义无效")
    METRIC_ROW_SELECTION_INVALID("指标行选择定义无效"),
    @Schema(description = "指标排序无法证明确定性")
    METRIC_ORDER_NOT_DETERMINISTIC("指标排序无法证明确定性"),
    @Schema(description = "指标执行模式不受支持")
    METRIC_EXECUTION_MODE_UNSUPPORTED("指标执行模式不受支持"),
    @Schema(description = "指标查询条件无效")
    QUERY_INVALID("指标查询条件无效"),
    @Schema(description = "指标查询结果无效")
    RESULT_INVALID("指标查询结果无效");

    /** 枚举描述。 */
    private final String desc;
}
