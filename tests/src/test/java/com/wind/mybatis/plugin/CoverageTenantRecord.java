package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.wind.integration.core.model.TenantIsolationObject;
import lombok.Getter;
import lombok.Setter;

/** 严格覆盖与复合查询共用的租户实体。 */
@Getter
@Setter
@Table(value = "coverage_tenant_record", mapperGenerateEnable = false)
public class CoverageTenantRecord implements TenantIsolationObject<Long> {

    @Id
    private Long id;

    private Long tenantId;
}
