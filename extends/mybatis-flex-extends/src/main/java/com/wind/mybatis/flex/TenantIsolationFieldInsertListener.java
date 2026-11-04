package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.TenantIsolationObject;

/**
 * 插入时使用当前租户覆盖 tenantId，缺少上下文时填 null。
 * Flex 按 Class 解析监听目标，父类泛型使用原始接口。
 *
 * @author wuxp
 * @since 2026-11-04
 */
public class TenantIsolationFieldInsertListener extends AbstractInsertListener<TenantIsolationObject> {

    @Override
    public void doInsert(TenantIsolationObject entity) {
        entity.setTenantId(DataIsolationContextHolder.getTenantId());
    }
}
