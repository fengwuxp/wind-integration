package com.wind.integration.system.model.dto;

import com.wind.integration.system.model.enums.WindConfigContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 数据字典
 *
 * @author wuxp
 * @since 2024-12-19
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(chain = true)
public class DictionaryMetadataDTO implements java.io.Serializable {

    @Schema(description = "语言类型")
    @NotNull
    private Locale locale;

    @Schema(description = "字典所属分组")
    @NotNull
    private String group;

    @Schema(description = "字典内容类型")
    @NotNull
    private WindConfigContentType contentType;

    @Schema(description = "字典内容")
    @NotBlank
    private String content;

    @Schema(description = "是否启用，默认 true")
    private Boolean enabled;

    @Schema(description = "租户id")
    @NotNull
    private Long tenantId;

    @Schema(description = "字典描述")
    private String description;

    @Schema(description = "字典名称")
    @NotBlank
    private String name;

    @Schema(description = "id")
    @NotNull
    private Long id;

    @Schema(description = "创建时间")
    @NotNull
    private LocalDateTime gmtCreate;

    @Schema(description = "修改时间")
    @NotNull
    private LocalDateTime gmtModified;

}
