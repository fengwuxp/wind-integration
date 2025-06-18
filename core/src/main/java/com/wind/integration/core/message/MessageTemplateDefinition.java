package com.wind.integration.core.message;

import org.springframework.lang.Nullable;

import jakarta.validation.constraints.NotBlank;

/**
 * 消息模板定义，通过消息模板生成消息
 *
 * @author wuxp
 * @date 2024-12-28 13:32
 **/
public interface MessageTemplateDefinition {

    /**
     * @return 模板名称
     */
    @NotBlank
    String getName();

    /**
     * @return 模板内容
     */
    @NotBlank
    String getContent();

    /**
     * @return 发送消息渠道的 template id
     */
    @Nullable
    String getChannelTemplateId();
}