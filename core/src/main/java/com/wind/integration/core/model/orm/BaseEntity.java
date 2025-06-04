package com.wind.integration.core.model.orm;

import com.wind.integration.core.model.IdObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用基础实体类
 *
 * @param <I> id 类型
 * @author wuxp
 * @date 2024-12-15 13:14
 **/
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString
public abstract class BaseEntity<I extends Serializable> implements IdObject<I> {

    /**
     * id
     */
    @NotNull
    private I id;

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
