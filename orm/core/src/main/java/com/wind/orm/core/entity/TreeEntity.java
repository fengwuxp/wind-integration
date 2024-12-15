package com.wind.orm.core.entity;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

/**
 * 树形实体
 *
 * @author wuxp
 * @date 2024-12-15 13:20
 **/
public abstract class TreeEntity<ID> extends NamedEntity<ID> {

    /**
     * 父资源
     */
    @Null
    private ID parentId;

    /**
     * 层级，0 表示根节点
     */
    @NotNull
    @Min(0)
    private Integer level;

    /**
     * 排序，默认 0
     */
    @NotNull
    private Integer orderIndex;

    /**
     * @return 是否为根节点
     */
    public boolean isRoot() {
        return level == 0;
    }
}
