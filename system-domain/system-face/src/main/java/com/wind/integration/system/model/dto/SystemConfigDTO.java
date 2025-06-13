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

/**
 * 系统配置
 *
 * @author wuxp
 * @since 2024-12-19
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(chain = true)
public class SystemConfigDTO implements java.io.Serializable {

    @Schema(description = "配置内容")
    @NotNull
    private String content;

    @Schema(description = "配置内容类型")
    @NotNull
    private WindConfigContentType contentType;

    @Schema(description = "配置分组")
    @NotBlank
    private String group;

    @Schema(description = "是否加密")
    private Boolean encryption;

    @Schema(description = "是否启用，默认 true")
    private Boolean enabled;

    @Schema(description = "配置描述")
    private String description;

    @Schema(description = "配置名称")
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
