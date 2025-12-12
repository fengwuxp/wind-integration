package com.wind.integration.im.spi;

import com.wind.websocket.chat.ImmutableChatMessage;

/**
 * 聊天消息存储仓库
 *
 * @author wuxp
 * @date 2025-12-12 14:59
 **/
public interface WindImChatMessageRepository {

    /**
     * 保存消息
     *
     * @param namespace 命名空间
     * @param message   消息
     */
    void save(String namespace, ImmutableChatMessage message);
}
