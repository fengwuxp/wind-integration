package com.wind.integration.im.session;

import com.wind.common.exception.AssertUtils;
import com.wind.integration.im.spi.WindImSessionService;
import com.wind.websocket.core.WindSessionConnectionPolicy;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketSession;
import com.wind.websocket.core.WindSocketSessionDescriptor;
import com.wind.websocket.core.WindSocketSessionRegistry;
import com.wind.websocket.core.WindSocketSessionStatus;
import com.wind.websocket.core.WindSocketSessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的 {@link WindSocketSessionRegistry} 实现类。
 * <p>
 * 管理 WebSocket 会话（{@link WindSocketSession}）的生命周期，包括创建、获取、更新、销毁等。
 * 所有会话都保存在内存中（基于 {@link ConcurrentHashMap} 的静态缓存）。
 * </p>
 *
 * @author wuxp
 * @date 2025-12-12 13:27
 **/
@AllArgsConstructor
@Slf4j
public class DefaultWindSocketSessionRegistry implements WindSocketSessionRegistry {

    /**
     * 本地内存缓存连接描述符
     * 该缓存存储连接ID对应的 WindSocketClientClientConnection 实例。
     * 注意：该缓存为本地进程内存，不适合多实例共享。
     */
    private final Map<String, WindSocketClientClientConnection> localConnectionDescriptorCache = new ConcurrentHashMap<>();

    private final RedissonClient redissonClient;

    private final WindImSessionService sessionService;

    /**
     * 获取指定 ID 的会话，如果不存在会抛出异常。
     *
     * @param sessionId 会话 ID
     * @return 已存在的会话对象
     * @throws IllegalArgumentException 如果会话不存在
     */
    @Override
    public @NotNull WindSocketSession getSession(@NotBlank String sessionId) {
        AssertUtils.hasText(sessionId, "sessionId cannot be empty");
        return buildDefaultWindSocketSession(sessionId);
    }

    /**
     * 销毁指定 ID 的会话，同时关闭其中的所有连接。
     *
     * @param sessionId 会话 ID
     */
    @Override
    public void destroySession(@NotBlank String sessionId) {
        DefaultWindSocketSession session = buildDefaultWindSocketSession(sessionId);
        Set<String> users = sessionService.getSessionMembers(sessionId);
        for (String userId : users) {
            // 移除会话成员
            session.removeUser(userId);
        }
        // 标记会话为‘已删除’
        WindSocketSessionDescriptor imSession = sessionService.getSession(sessionId);
        sessionService.updateSessionStatus(imSession.getId(), WindSocketSessionStatus.DELETED);
    }

    /**
     * 判断指定 ID 的会话是否存在。
     *
     * @param sessionId 会话 ID
     * @return 是否存在
     */
    @Override
    public boolean exists(@NotBlank String sessionId) {
        return sessionService.exists(sessionId);
    }

    @Override
    public void activeSession(@NotBlank String sessionId) {
        sessionService.updateSessionStatus(sessionId, WindSocketSessionStatus.ACTIVE);
    }

    @Override
    public void suspendSession(@NotBlank String sessionId) {
        sessionService.updateSessionStatus(sessionId, WindSocketSessionStatus.SUSPENDED);
    }

    private DefaultWindSocketSession buildDefaultWindSocketSession(@NotBlank String sessionId) {
        WindSocketSessionDescriptor session = sessionService.getSession(sessionId);
        Map<String, Object> metadata = Objects.requireNonNullElse(session.getMetadata(), Collections.emptyMap());
        LocalDateTime gmtCreate = session.getGmtCreate();
        WindSocketSessionStatus state = session.getStatus();
        WindSessionConnectionPolicy connectionPolicy = session.getSessionConnectionPolicy();
        WindSocketSessionType type = session.getSessionType();
        return new DefaultWindSocketSession(sessionId, gmtCreate, state, connectionPolicy, type, metadata, redissonClient, sessionService, localConnectionDescriptorCache);
    }
}

