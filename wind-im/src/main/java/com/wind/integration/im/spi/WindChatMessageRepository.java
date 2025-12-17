package com.wind.integration.im.spi;

import com.wind.websocket.chat.WindChatMessage;
import org.jspecify.annotations.NonNull;

/**
 * 聊天消息存储仓库
 *
 * @author wuxp
 * @date 2025-12-12 14:59
 **/
public interface WindChatMessageRepository {

    /**
     * 保存消息
     *
     * @param message 消息
     */
    void save(@NonNull WindChatMessage message);
}
