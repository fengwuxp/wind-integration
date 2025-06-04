package com.wind.integration.system.model.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 配置内容类型
 *
 * @author wuxp
 * @date 2024-12-16 09:40
 **/
@AllArgsConstructor
@Getter
public enum SystemConfigContentType implements DescriptiveEnum {

    /**
     * json
     */
    JSON("JSON"),

    /**
     * text
     */
    TEXT("TEXT"),
    ;

    private final String desc;
}
