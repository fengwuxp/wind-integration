package com.wind.integration.im;

/**
 * @author wuxp
 * @date 2025-12-12 10:48
 **/
public final class WindImConstants {

    private WindImConstants() {
        throw new AssertionError();
    }

    /**
     * SocketIO 连接成功
     */
    public static final String SOCKETIO_CONNECTION_SUCCESS_EVENT = "socketio.connection.success";

    /**
     * 聊天消息发送
     */
    public static final String CHAT_MESSAGE_SENT_EVENT = "chat.message.sent";

    /**
     * 聊天消息撤回
     */
    public static final String CHAT_MESSAGE_REVOKE_EVENT = "chat.message.revoke";

    /**
     * 聊天消息撤回失败
     */
    public static final String CHAT_MESSAGE_REVOKE_FAILURE_EVENT = "chat.message.revoke-failure";

    /**
     * 聊天消息路由
     */
    public static final String ROUTE_CHAT_MESSAGE_PATH = "/inc/api/v1/im/route/message";

    /**
     * 撤回消息路由
     */
    public static final String ROUTE_REVOKE_MESSAGE_PATH = "/inc/api/v1/im/route/revoke";

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
