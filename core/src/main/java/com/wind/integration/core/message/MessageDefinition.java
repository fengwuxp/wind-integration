package com.wind.integration.core.message;


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
    String getTitle();

    /**
     * @return 消息内容
     */
    T getMessage();

    /**
     * 消息格式
     *
     * @return 例如：html\text\markdown 等
     */
    String getFormat();

    /**
     * 根据不同的消息发送渠道可以是手机号码、邮箱地址、等
     *
     * @return 消息接收者
     */
    Set<String> getReceivers();

}
