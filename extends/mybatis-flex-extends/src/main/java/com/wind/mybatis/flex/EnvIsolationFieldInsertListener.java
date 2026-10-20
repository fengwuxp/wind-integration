package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.EnvIsolationObject;

/**
 * 环境隔离字段插入时自动设置 {@link com.mybatisflex.annotation.InsertListener}
 *
 * @author wuxp
 * @date 2024-12-15 13:09
 **/
public class EnvIsolationFieldInsertListener extends AbstractInsertListener<EnvIsolationObject> {

    @Override
    public void doInsert(EnvIsolationObject entity) {
        entity.setEnv(DataIsolationContextHolder.requireEnv());
    }

}
