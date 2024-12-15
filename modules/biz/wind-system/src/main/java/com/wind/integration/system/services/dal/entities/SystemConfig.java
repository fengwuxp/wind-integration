package com.wind.integration.system.services.dal.entities;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.wind.orm.core.entity.NamedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * @author wuxp
 * @date 2023-09-27 16:17
 **/
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Table(SystemConfig.TABLE_NAME)
public class SystemConfig extends NamedEntity<Long> implements Serializable {

    public static final String TABLE_NAME = "t_system_config";

    /**
     * 配置内容
     */
    @NotNull
    @Column("config_content")
    private String content;

    /**
     * 配置分组
     */
    @NotBlank
    @Column("config_group")
    private String group;

    /**
     * 是否加密
     */
    @Column("is_encryption")
    private Boolean encryption;

    /**
     * 是否启用，默认 true
     */
    @Column("is_enabled")
    private Boolean enabled;

    /**
     * 配置描述
     */
    private String description;

}
