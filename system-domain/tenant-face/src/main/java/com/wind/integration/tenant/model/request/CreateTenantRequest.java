package com.wind.integration.tenant.model.request;

import com.wind.integration.tenant.model.dto.TenantOwner;
import com.wind.integration.tenant.model.enums.TenantOwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建租户请求
 *
 * @author wuxp
 * @date 2025-06-13 11:15
 **/
@Data
@EqualsAndHashCode
@Accessors(chain = true)
public class CreateTenantRequest {

    @Schema(description = "租户名称")
    @NotBlank
    @Size(min = 2, max = 20)
    private String tenantName;

    @Schema(description = "租户所有者")
    private TenantOwner owner;

    @Schema(description = "租户 owner 类型")
    private TenantOwnerType ownerType;

    @Schema(description = "编码")
    @Size(min = 2, max = 50)
    private String code;

    @Schema(description = "联系人")
    @Size(max = 50)
    private String contacts;

    @Schema(description = "联系人电话")
    @Size(max = 20)
    private String contactsMobilePhone;

    @Schema(description = "是否启用")
    private Boolean enabled = true;

    @Schema(description = "备注")
    @Size(max = 150)
    private String remark;


}
