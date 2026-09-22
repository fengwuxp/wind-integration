package com.wind.integration.metrics.dsl.materialization;

import com.wind.integration.metrics.enums.MetricSnapshotStorageType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 物化计划中的逻辑保存目标。
 *
 * <p>描述结果对象类型及逻辑保存形态，不包含工厂实现类、物理数据源、表名、列名或凭据。
 * 消费端须按已发布的计划版本和分段冻结物理绑定，运行、重试和恢复不得重新解析当前物理目标。
 * 该合同不限定一次物化的时间范围，也不表示已完成至当前时间。</p>
 *
 * <p>两种保存形态均由消费端根据冻结的指标定义、计划和分段上下文推导完整唯一键，供查询与写回共同使用。
 * 唯一键须保留主体、完整维度、时间桶及指标结果、版本等必要隔离，其物理实现由适配器承接。
 * GLOBAL 的身份同样由上下文推导，不要求业务构造占位键。</p>
 *
 * <p>宿主将 objectTypeClassName 解析为
 * {@link com.wind.integration.metrics.WindMetricsAggregatorFactory#factory(Class)} 的目标类型。
 * 指标来源由计划成员及其固定定义决定：宽表 SCALAR 使用成员编码同名属性，FIELD_SET 使用
 * 定义内同名字段；缺属性、类型不符或字段冲突须拒绝，不自动产生别名或覆盖。
 * 行式快照按成员和值字段逐条构造完整保存对象。宿主负责类可构造、实际保存者、必要合并状态
 * 及读写一致性校验；公共 DSL 不加载该类，也不重复声明字段映射。</p>
 *
 * @param storageType 逻辑保存形态
 * @param bucketTimeField 快照对象中实际覆盖末端的属性名，例如 endTime；该值是不包含的最后快照水位，不是计算完成时间或计划目标时间
 * @param objectTypeClassName 聚合结果对象的全限定二进制类名，对应 factory 的 objectType 入参
 *
 * @author wuxp
 * @date 2026-09-14 15:30
 */
@Schema(description = "物化计划中的逻辑保存目标及聚合结果类型，不包含物理绑定")
public record MetricSnapshotTargetDsl(
        @Schema(description = "逻辑保存形态") MetricSnapshotStorageType storageType,
        @Schema(description = "快照对象的实际覆盖末端属性名，例如 endTime；左闭右开区间的最后快照水位") String bucketTimeField,
        @Schema(description = "聚合结果对象全限定类名，对应 factory(Class) 的目标类型") String objectTypeClassName) {

    public MetricSnapshotTargetDsl {
        Objects.requireNonNull(storageType, "storageType must not be null");
        Objects.requireNonNull(bucketTimeField, "bucketTimeField must not be null");
        Objects.requireNonNull(objectTypeClassName, "objectTypeClassName must not be null");
    }
}
