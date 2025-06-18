package com.wind.integration.system.services.dal.entities;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.wind.integration.core.model.orm.NamedEntity;
import com.wind.integration.system.model.enums.WindConfigContentType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 系统配置表
 *
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
     * 配置内容类型
     */
    @NotNull
    private WindConfigContentType contentType;

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
