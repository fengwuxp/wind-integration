package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.SettableWorkspaceIsolationObject;

/**
 * 为可设置工作空间归属的实体填充当前工作空间，缺少上下文时填 null。
 * Flex 按 Class 解析监听目标，父类泛型必须使用原始接口，不能传入带类型参数的接口。
 *
 * @author wuxp
 * @date 2024-12-15 13:09
 **/
public class WorkspaceIsolationFieldInsertListener extends AbstractInsertListener<SettableWorkspaceIsolationObject> {

    @Override
    public void doInsert(SettableWorkspaceIsolationObject entity) {
        entity.setWorkspaceId(DataIsolationContextHolder.getWorkspaceId());
    }

}
