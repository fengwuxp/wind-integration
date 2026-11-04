package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Table;

/** 同表继承映射沿用父实体字段与隔离规则，用于验证等价登记不会遗漏 requiredEntities。 */
@Table(value = "coverage_tenant_record", mapperGenerateEnable = false)
public class CoverageTenantAliasRecord extends CoverageTenantRecord {
}
