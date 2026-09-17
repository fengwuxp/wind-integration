package com.wind.integration.metrics.query;

import org.jspecify.annotations.Nullable;

import static com.wind.integration.metrics.enums.MetricErrorCode.QUERY_INVALID;
import static com.wind.integration.metrics.query.MetricQueryValueSupport.error;

/**
 * 正式 DSL 查询及其实时试算入口的公共条件校验。
 *
 * <p>通用聚合条件不在构造时套用 DSL 限制。服务实现先调用本校验，再按实际定义核对
 * 主体类型、完整维度、参数声明和边界；本类不选择指标、修订、路线或事务。</p>
 *
 * @author wuxp
 * @since 2026-09-15
 */
public final class MetricQueryValidator {

    private MetricQueryValidator() {
    }

    /**
     * 校验 DSL 的单主体、必填半开窗口、标量维度、整数参数及无标签限制。
     *
     * @param criteria 待执行的公共条件
     * @throws com.wind.integration.metrics.MetricValidationException 条件不能用于正式 DSL 查询
     */
    public static void validateDsl(@Nullable MetricQuery criteria) {
        if (criteria == null) {
            throw error(QUERY_INVALID, "", "Query criteria must not be null");
        }
        Object subjectId = criteria.subjectId();
        if (subjectId != null && (!(subjectId instanceof String id) || id.isBlank())) {
            throw error(QUERY_INVALID, "/subjectId", "DSL subjectId must be a non-blank string");
        }
        if (criteria.subjectType() != null && criteria.subjectType().isBlank()) {
            throw error(QUERY_INVALID, "/subjectType", "subjectType must not be blank");
        }
        MetricQueryValueSupport.validateWindow(criteria.startTime(), criteria.endTime(), QUERY_INVALID);
        MetricQueryValueSupport.immutableDimensions(criteria.dimensionValues());
        MetricQueryValueSupport.immutableParameters(criteria.parameterValues());
        if (criteria.searchTags() != null && !criteria.searchTags().isEmpty()) {
            throw error(QUERY_INVALID, "/searchTags", "DSL queries do not support searchTags");
        }
    }
}
