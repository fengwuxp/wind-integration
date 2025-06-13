package com.wind.integration.system.model.rquest;

import com.wind.integration.system.model.enums.WindConfigContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
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
public class SaveDictionaryMetadataRequest {

    @Schema(description = "语言类型")
    private Locale locale;

    @Schema(description = "字典所属分组")
    private String group;

    @Schema(description = "字典所属业务分组")
    private String businessGroup;

    @Schema(description = "字典内容类型")
    private WindConfigContentType contentType;

    @Schema(description = "字典内容")
    private String content;

    @Schema(description = "是否启用，默认 true")
    private Boolean enabled;

    @Schema(description = "租户id")
    private Long tenantId;

    @Schema(description = "字典描述")
    private String description;

    @Schema(description = "字典名称")
    private String name;

    @Schema(description = "id")
    @NotNull
    private Long id;

}
