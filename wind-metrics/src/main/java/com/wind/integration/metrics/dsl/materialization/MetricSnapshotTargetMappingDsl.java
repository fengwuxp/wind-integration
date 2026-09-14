package com.wind.integration.metrics.dsl.materialization;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 物化目标中的逻辑结果映射。
 *
 * <p>SCALAR 映射其唯一业务结果，字段名指定逻辑保存名称；FIELD_SET 按字段名引用固定定义中
 * {@code fields} 的同名业务输出，同一指标可通过多条映射表达多个输出。
 * 消费端负责解析真实业务输出及物理绑定；本映射不描述 SUM、COUNT 等内部计算状态，
 * 也不免除消费端保存跨桶合并所需状态的责任。</p>
 *
 * <p>字段名属于指标编码的命名空间。相同指标编码和字段名的重复声明保留，
 * 消费端应复用同一固定来源和绑定，形成一个有效结果位置，不得重复累计业务结果。</p>
 *
 * @param metricCode 计划成员指标编码
 * @param fieldName 逻辑结果字段名；FIELD_SET 同时引用固定定义的同名业务输出
 *
 * @author wuxp
 * @date 2026-09-14 15:30
 */
@Schema(description = "物化目标中的逻辑结果映射，不包含物理列绑定")
public record MetricSnapshotTargetMappingDsl(
        @Schema(description = "计划成员指标编码") String metricCode,
        @Schema(description = "逻辑结果字段名；FIELD_SET 引用固定定义的同名业务输出") String fieldName) {

    public MetricSnapshotTargetMappingDsl {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
        Objects.requireNonNull(fieldName, "fieldName must not be null");
    }
}
