/**
 * 指标定义规范：描述一个指标以何种方式声明，以及该声明的结构化内容。
 *
 * <p>{@link com.wind.integration.metrics.spec.MetricDefinitionSpec} 是规范根容器，持有
 * {@code schemaVersion} 与定义对象；{@link com.wind.integration.metrics.spec.MetricDefinitionObject}
 * 是定义内容的封闭抽象。两者均只描述计算规则，不表示指标值、查询路线或物化状态——
 * 指标值由 {@code WindMetricsValue} 承担，查询路线由 {@code MetricQueryMode} 表达。</p>
 *
 * <p>规范的解析、校验与规范化 JSON 输出由 {@code json} 包的 codec 承接，本包不含解析逻辑。</p>
 *
 * @author wuxp
 * @since 2026-09-18
 */
package com.wind.integration.metrics.spec;
