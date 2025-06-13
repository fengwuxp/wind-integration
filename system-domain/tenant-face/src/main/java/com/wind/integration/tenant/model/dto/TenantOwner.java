package com.wind.integration.tenant.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import static com.wind.integration.core.constant.CommonlyFieldValidationConstants.PASSWORD_MESSAGE;
import static com.wind.integration.core.constant.CommonlyFieldValidationConstants.PASSWORD_REGEXP;

/**
 * 租户 owner
 *
 * @author wuxp
 * @date 2025-06-13 11:31
 **/
@Schema(description = "租户所有者")
@Data
@EqualsAndHashCode
@ToString(callSuper = true, exclude = "password")
public class TenantOwner {

    @Schema(description = "所有者手机号码区号")
    @Size(max = 10)
    @NotBlank
    private String countryCallingCode = "+86";

    @Schema(description = "所有者手机号码（手机、邮箱二选一）")
    @Size(max = 20)
    private String mobilePhone;

    @Schema(description = "所有者邮箱（手机、邮箱二选一）")
    @Size(max = 20)
    private String email;

    @Schema(description = "密码（允许不设置，默认同手机号或邮箱），使用默认密码时，首次必须进行更改")
    @Pattern(regexp = PASSWORD_REGEXP, message = PASSWORD_MESSAGE)
    private String password;
}
