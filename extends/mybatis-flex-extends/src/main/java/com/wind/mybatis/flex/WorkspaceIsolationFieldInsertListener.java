package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.WorkspaceIsolationObject;

/**
 * 工作空间字段插入时自动设置 {@link com.mybatisflex.annotation.InsertListener}。
 * Flex 按 Class 解析监听目标，父类泛型必须使用原始接口，不能传入带类型参数的接口。
 *
 * @author wuxp
 * @date 2024-12-15 13:09
 **/
public class WorkspaceIsolationFieldInsertListener extends AbstractInsertListener<WorkspaceIsolationObject> {

    @Override
    public void doInsert(WorkspaceIsolationObject entity) {
        entity.setWorkspaceId(DataIsolationContextHolder.getWorkspaceId());
    }

}
