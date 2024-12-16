package com.wind.integration.core.model.orm;

import com.wind.integration.core.model.TenantIsolationObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

/**
 * 租户隔离实体
 *
 * @author wuxp
 * @date 2024-12-15 13:19
 **/
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString
public abstract class TenantIsolationEntity<ID> extends BaseEntity<ID> implements TenantIsolationObject<ID> {

    @NotNull
    private ID tenantId;
}
