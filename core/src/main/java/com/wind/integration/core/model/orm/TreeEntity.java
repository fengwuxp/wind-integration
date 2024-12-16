package com.wind.integration.core.model.orm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

/**
 * 树形实体
 *
 * @author wuxp
 * @date 2024-12-15 13:20
 **/
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString
public abstract class TreeEntity<ID> extends NamedEntity<ID> {

    /**
     * 父资源，为 null 表示根节点
     */
    @Null
    private ID parentId;

    /**
     * 层级
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
        return parentId == null;
    }
}
