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
public enum WindConfigContentType implements DescriptiveEnum {

    /**
     * json
     */
    JSON("Json"),

    /**
     * text
     */
    TEXT("Text"),

    HTML("Html"),
    ;

    private final String desc;
}
