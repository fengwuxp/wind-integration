package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.SettableTenantIsolationObject;

import java.io.Serializable;

/**
 * 将当前租户写入实体。未绑定租户时保留实体的租户值。
 * Flex 按 Class 解析监听目标，父类泛型使用原始接口。
 *
 * @author wuxp
 * @since 2026-11-04
 */
public class TenantIsolationFieldInsertListener extends AbstractInsertListener<SettableTenantIsolationObject> {

    @Override
    public void doInsert(SettableTenantIsolationObject entity) {
        Serializable tenantId = DataIsolationContextHolder.getTenantId();
        if (tenantId != null) {
            entity.setTenantId(tenantId);
        }
    }
}
