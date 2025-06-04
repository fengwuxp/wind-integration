package com.wind.integration.core.model.orm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

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
public abstract class NamedEntity<I extends Serializable> extends BaseEntity<I> {

    @NotBlank
    private String name;
}
