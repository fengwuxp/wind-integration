package com.wind.integration.im;

import com.wind.integration.core.constant.WindInternalApiConstants;

/**
 * @author wuxp
 * @date 2025-12-12 10:48
 **/
public final class WindImConstants {

    private WindImConstants() {
        throw new AssertionError();
    }

    /**
     * 用户连接会话
     */
    public static final String CHAT_SESSION_JOIN_EVENT = "chat.session.joined";

    /**
     * 会话状态激活事件
     */
    public static final String CHAT_SESSION_STATUS_CHANGED_EVENT = "chat.session.status.changed";

    /**
     * 聊天消息发送
     */
    public static final String CHAT_MESSAGE_SENT_EVENT = "chat.message.sent";

    /**
     * 聊天消息撤回
     */
    public static final String CHAT_MESSAGE_REVOKE_EVENT = "chat.message.revoked";

    /**
     * 聊天消息撤回失败
     */
    public static final String CHAT_MESSAGE_REVOKE_FAILURE_EVENT = "chat.message.revoke.failed";

    /**
     * 聊天消息路由
     */
    public static final String ROUTE_CHAT_MESSAGE_PATH = WindInternalApiConstants.INC_BASIC_API_PREFIX + "/im/route/message";

    /**
     * 撤回消息路由
     */
    public static final String ROUTE_REVOKE_MESSAGE_PATH = WindInternalApiConstants.INC_BASIC_API_PREFIX + "/im/route/revoked-message";

    /**
     * 会话状态路由
     */
    public static final String ROUTE_SESSION_STATUS_MESSAGE_PATH = WindInternalApiConstants.INC_BASIC_API_PREFIX + "/im/route/session-status";

    /**
     * 用户名
     */
    public static final String USERNAME_VARIABLE_NAME = "username";

    /**
     * 命名空间
     */
    public static final String NAMESPACE_VARIABLE_NAME = "namespace";

    /**
     * 会话 ID
     */
    public static final String SESSION_ID_VARIABLE_NAME = "sessionId";

    /**
     * 会话名称
     */
    public static final String SESSION_NAME_VARIABLE_NAME = "sessionName";

    /**
     * 连接 ID
     */
    public static final String CONNECTION_ID_VARIABLE_NAME = "connectionId";

    /**
     * 节点 IP 地址
     */
    public static final String NODE_IP_ADDRESS_VARIABLE_NAME = "nodeIpAddress";

    /**
     * 语言
     */
    public static final String LOCALE_VARIABLE_NAME = "locale";

}
