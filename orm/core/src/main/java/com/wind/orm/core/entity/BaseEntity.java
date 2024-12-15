package com.wind.orm.core.entity;

import com.wind.integration.core.model.IdObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 通用基础实体类
 *
 * @author wuxp
 * @date 2024-12-15 13:14
 **/
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString
public abstract class BaseEntity<ID> implements IdObject<ID> {

    /**
     * id
     */
    @NotNull
    private ID id;

    /**
     * 创建时间
     */
    @NotNull
    private LocalDateTime gmtCreate;

    /**
     * 修改时间
     */
    @NotNull
    private LocalDateTime gmtModified;
}
