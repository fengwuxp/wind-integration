package com.wind.integration.core.model;

import java.io.Serializable;

/**
 * 可设置工作空间归属的对象。
 * 在 {@link WorkspaceIsolationObject} 的读取契约上增加赋值能力，
 * 供实体插入填充等场景使用；工作空间来源与赋值时机由调用方决定。
 *
 * @param <W> 工作空间 ID 类型
 * @author wuxp
 * @since 2026-11-05
 */
public interface SettableWorkspaceIsolationObject<W extends Serializable> extends WorkspaceIsolationObject<W> {

    /**
     * 设置对象所属的工作空间。
     *
     * @param workspaceId 工作空间 ID，null 表示未绑定
     */
    void setWorkspaceId(W workspaceId);
}
