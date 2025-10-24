package com.wind.integration.core.message;


import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 消息定义，用于适配不同发送的方式或业务场景
 *
 * @author wuxp
 * @date 2023-09-28 15:27
 * @see MessageSender
 * @see TemplateMessageFactory
 */
public interface MessageDefinition<T> {

    /**
     * @return 消息标题
     */
    @NotNull
    String getTitle();

    /**
     * @return 消息内容
     */
    @NotNull
    T getMessage();

    /**
     * 消息格式
     *
     * @return 例如：html\text\markdown 等
     */
    @NotNull
    String getFormat();

    /**
     * 根据不同的消息发送渠道可以是手机号码、邮箱地址、等
     *
     * @return 消息接收者
     */
    @NotNull
    Set<String> getReceivers();

    /**
     * 获取消息元数据
     *
     * @return 元数据
     */
    @NotNull
    default Map<String, Object> getMetadata() {
        return Collections.emptyMap();
    }
}
