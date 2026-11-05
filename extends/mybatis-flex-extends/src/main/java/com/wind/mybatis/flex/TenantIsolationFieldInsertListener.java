package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.SettableTenantIsolationObject;

/**
 * 为可设置租户归属的实体填充当前租户，缺少上下文时填 null。
 * Flex 按 Class 解析监听目标，父类泛型使用原始接口。
 *
 * @author wuxp
 * @since 2026-11-04
 */
public class TenantIsolationFieldInsertListener extends AbstractInsertListener<SettableTenantIsolationObject> {

    @Override
    public void doInsert(SettableTenantIsolationObject entity) {
        entity.setTenantId(DataIsolationContextHolder.getTenantId());
    }
}
