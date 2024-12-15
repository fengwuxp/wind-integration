package com.wind.orm.core.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

/**
 * 有名字的实体
 *
 * @author wuxp
 * @date 2024-12-15 13:19
 **/
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString
public abstract class NamedEntity<ID> extends BaseEntity<ID> {

    @NotBlank
    private String name;
}
