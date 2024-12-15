package com.wind.orm.core.entity;

import javax.validation.constraints.NotBlank;

/**
 * 有名字的实体
 *
 * @author wuxp
 * @date 2024-12-15 13:19
 **/
public abstract class NamedEntity<ID> extends BaseEntity<ID> {

    @NotBlank
    private String name;
}
