package com.wind.integration.tenant.model.enums;


import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 租户 owner 类型
 *
 * @author laisr
 * @since 2023-10-22
 */
@Getter
@AllArgsConstructor
public enum TenantOwnerType implements DescriptiveEnum {

    /**
     * 平台自营
     */
    PLATFORM("自营"),

    /**
     * 企业
     */
    ENTERPRISE("企业"),

    /**
     * 个人
     */
    INDIVIDUAL("个人"),

    /**
     * 其他
     */
    OTHER("其他");


    private final String desc;


}
