package com.wind.integration.im.spi;

import com.wind.websocket.core.WindSocketSessionDescriptor;
import com.wind.websocket.core.WindSocketSessionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * IM 会话服务
 *
 * @author wuxp
 * @date 2025-12-12 13:19
 **/
public interface WindImSessionService {

    /**
     * 创建会话
     *
     * @param sessionId 会话 id，可能为空
     * @param name      会话名称
     * @param metadata  会话元数据
     * @return 创建的会话描述符
     */
    String createIfAbsent(@Nullable String sessionId, @NonNull String name, @NonNull Map<String, Object> metadata);

    /**
     * 更新会话状态
     *
     * @param sessionId 会话 id
     * @param status    会话状态
     */
    void updateSessionStatus(@NonNull String sessionId, @NonNull WindSocketSessionStatus status);

    /**
     * 更新会话状态
     *
     * @param sessionId   会话 id
     * @param sessionName 会话名称
     * @param metadata    会话元数据
     */
    void updateSessionMetadata(@NonNull String sessionId, @NonNull String sessionName, @NonNull Map<String, Object> metadata);

    /**
     * 判断会话是否存在
     *
     * @param sessionId 会话 id
     * @return 是否存在
     */
    boolean exists(@NonNull String sessionId);

    /**
     * 获取会话
     *
     * @param sessionId 会话 id
     * @return 会话描述符
     */
    @NonNull
    WindSocketSessionDescriptor getSession(@NonNull String sessionId);

    /**
     * 判断用户是否在会话中
     *
     * @param sessionId 会话 id
     * @param userId    用户 id
     * @return 是否在会话中
     */
    boolean sessionExistUser(@NonNull String sessionId, @NonNull String userId);

    /**
     * 获取会话中的所有用户
     *
     * @param sessionId 会话 id
     * @return 会话中的所有用户
     */
    @NonNull
    Set<String> getSessionMembers(@NonNull String sessionId);
}
