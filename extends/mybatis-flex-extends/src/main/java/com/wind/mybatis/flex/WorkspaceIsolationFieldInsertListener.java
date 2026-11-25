package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.SettableWorkspaceIsolationObject;

import java.io.Serializable;

/**
 * 将当前工作空间写入实体。未绑定工作空间时保留实体的工作空间值。
 * Flex 按 Class 解析监听目标，父类泛型必须使用原始接口，不能传入带类型参数的接口。
 *
 * @author wuxp
 * @date 2024-12-15 13:09
 **/
public class WorkspaceIsolationFieldInsertListener extends AbstractInsertListener<SettableWorkspaceIsolationObject> {

    @Override
    public void doInsert(SettableWorkspaceIsolationObject entity) {
        Serializable workspaceId = DataIsolationContextHolder.getWorkspaceId();
        if (workspaceId != null) {
            entity.setWorkspaceId(workspaceId);
        }
    }

}
