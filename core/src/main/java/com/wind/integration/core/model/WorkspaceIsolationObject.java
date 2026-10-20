package com.wind.integration.core.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * 声明对象所属的工作空间；与租户归属、对象主键独立，不负责成员权限或上下文存储。
 *
 * @param <W> 工作空间 ID 类型
 * @author wuxp
 * @since 2026-10-19
 */
public interface WorkspaceIsolationObject<W extends Serializable> {

    /**
     * 获取对象所属的工作空间 ID。
     *
     * @return 工作空间 ID
     */
    @NonNull
    W getWorkspaceId();

    /**
     * 设置工作空间 ID
     *
     * @param workspaceId 工作空间 ID
     */
    void setWorkspaceId(@Nullable W workspaceId);
}
