package com.wind.integration.core.message;

import java.util.Collection;

/**
 * 消息发送者
 *
 * @author wuxp
 * @version MessageSender.java, v 0.1 2023年03月22日 10:07 wuxp
 */
public interface MessageSender<M extends MessageDefinition<?>> {

    /**
     * 发送消息
     *
     * @param message 消息
     */
    void sendMessage(M message);

    /**
     * 批量发送消息
     *
     * @param messages 消息列表
     */
    default void sendMessages(Collection<M> messages) {
        messages.forEach(this::sendMessage);
    }

    /**
     * 是否支持发送该消息
     *
     * @param messageType 消息类类型
     * @return true 支持发送
     */
    default boolean supports(Class<? extends M> messageType) {
        return true;
    }

}