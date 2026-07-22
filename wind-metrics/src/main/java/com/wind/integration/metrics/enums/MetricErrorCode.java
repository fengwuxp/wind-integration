package com.wind.integration.metrics.enums;

import com.wind.common.enums.DescriptiveEnum;
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
public enum MetricErrorCode implements DescriptiveEnum {

    DSL_JSON_INVALID("DSL JSON 格式无效"),
    DSL_ROOT_NOT_OBJECT("DSL 根节点不是对象"),
    DSL_FIELD_DUPLICATED("DSL 字段重复"),
    DSL_FIELD_UNKNOWN("DSL 字段未知"),
    DSL_FIELD_REQUIRED("DSL 必填字段缺失"),
    DSL_FIELD_TYPE_INVALID("DSL 字段类型无效"),
    DSL_SCHEMA_VERSION_UNSUPPORTED("DSL 版本不受支持"),
    DSL_IDENTIFIER_INVALID("DSL 标识符无效"),
    DSL_VALUE_BRANCH_INVALID("DSL 指标值分支无效"),
    DSL_VALUE_INVALID("DSL 指标值无效"),
    DSL_PLAN_INVALID("DSL 物化计划无效"),
    QUERY_INVALID("指标查询条件无效"),
    RESULT_INVALID("指标查询结果无效");

    /** 枚举描述。 */
    private final String desc;
}
