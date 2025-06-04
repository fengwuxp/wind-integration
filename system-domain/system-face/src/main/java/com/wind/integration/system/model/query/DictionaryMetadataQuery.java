package com.wind.integration.system.model.query;

import com.wind.common.query.supports.AbstractPageQuery;
import com.wind.common.query.supports.DefaultOrderField;
import com.wind.integration.system.model.enums.SystemConfigContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 数据字典
 *
 * @author wuxp
 * @since 2024-12-19
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Accessors(chain = true)
public class DictionaryMetadataQuery extends AbstractPageQuery<DefaultOrderField> {

    @Schema(description = "语言类型")
    private String locale;

    @Schema(description = "字典所属分组")
    private String group;

    @Schema(description = "字典内容类型")
    private SystemConfigContentType contentType;

    @Schema(description = "是否启用，默认 true")
    private Boolean enabled;

    @Schema(description = "租户id")
    private Long tenantId;

    @Schema(description = "字典描述")
    private String description;

    @Schema(description = "字典名称")
    private String name;

    @Schema(description = "查询到最小gmtCreate")
    private LocalDateTime minGmtCreate;

    @Schema(description = "查询到最大gmtCreate")
    private LocalDateTime maxGmtCreate;

    @Schema(description = "查询到最小gmtModified")
    private LocalDateTime minGmtModified;

    @Schema(description = "查询到最大gmtModified")
    private LocalDateTime maxGmtModified;

}
