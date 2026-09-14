package com.wind.integration.metrics.dsl.materialization;

import com.wind.integration.metrics.enums.MetricSnapshotStorageType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * 物化计划中的逻辑保存目标。
 *
 * <p>只描述结果行的逻辑保存合同，不包含目标标识、物理数据源、表名、列名或凭据。
 * 消费端须按已发布的计划版本和分段冻结物理绑定，运行、重试和恢复不得重新解析当前物理目标。
 * 该合同不限定一次物化的时间范围，也不表示已完成至当前时间。</p>
 *
 * <p>两种保存形态均由消费端根据冻结的指标定义、计划和分段上下文推导完整唯一键，供查询与写回共同使用。
 * 唯一键须保留主体、完整维度、时间桶及指标结果、版本等必要隔离，其物理实现由适配器承接。
 * GLOBAL 的身份同样由上下文推导，不要求业务构造占位键。</p>
 *
 * @param storageType 逻辑保存形态
 * @param bucketTimeField 结果行的逻辑时间字段，不是运行水位
 * @param valueMappings 指标结果到逻辑结果字段的映射，规范化按指标编码和字段名排序
 *
 * @author wuxp
 * @date 2026-09-14 15:30
 */
@Schema(description = "物化计划中的逻辑保存目标及结果映射，不包含物理绑定")
public record MetricSnapshotTargetDsl(
        @Schema(description = "逻辑保存形态") MetricSnapshotStorageType storageType,
        @Schema(description = "结果行的逻辑时间字段，不是运行水位") String bucketTimeField,
        @Schema(description = "指标结果到逻辑结果字段的映射，非空") List<MetricSnapshotTargetMappingDsl> valueMappings) {

    public MetricSnapshotTargetDsl {
        Objects.requireNonNull(storageType, "storageType must not be null");
        Objects.requireNonNull(bucketTimeField, "bucketTimeField must not be null");
        valueMappings = List.copyOf(valueMappings);
    }
}
