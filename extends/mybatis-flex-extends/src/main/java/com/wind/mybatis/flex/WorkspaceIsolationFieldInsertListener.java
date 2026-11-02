package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.WorkspaceIsolationObject;

import java.io.Serializable;

/**
 * 工作空间字段插入时自动设置 {@link com.mybatisflex.annotation.InsertListener}
 *
 * @author wuxp
 * @date 2024-12-15 13:09
 **/
public class WorkspaceIsolationFieldInsertListener extends AbstractInsertListener<WorkspaceIsolationObject<? extends Serializable>> {

    @Override
    public void doInsert(WorkspaceIsolationObject<? extends Serializable> entity) {
        entity.setWorkspaceId(DataIsolationContextHolder.getWorkspaceId());
    }

}
