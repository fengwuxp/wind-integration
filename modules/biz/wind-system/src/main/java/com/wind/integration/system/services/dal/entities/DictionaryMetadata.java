package com.wind.integration.system.services.dal.entities;


import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.wind.integration.core.model.orm.NamedEntity;
import com.wind.integration.system.model.enums.WindConfigContentType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Locale;

/**
 * 数据字典元数据
 *
 * @author wuxp
 * @date 2024-12-16
 **/
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Table(DictionaryMetadata.TABLE_NAME)
public class DictionaryMetadata extends NamedEntity<Long> implements Serializable {

    public static final String TABLE_NAME = "t_dictionary_metadata";

    /**
     * 语言类型
     */
    @NotNull
    private Locale locale;

    /**
     * 字典所属分组
     */
    @NotNull
    @Column(value = "dictionary_group")
    private String group;

    /**
     * 字典内容类型
     */
    @NotNull
    private WindConfigContentType contentType;

    /**
     * 字典内容
     */
    @NotBlank
    private String content;

    /**
     * 是否启用，默认 true
     */
    @Column("is_enabled")
    private Boolean enabled;

    /**
     * 租户id
     */
    @NotNull
    private Long tenantId;

    /**
     * 字典描述
     */
    private String description;

}
