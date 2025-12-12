package com.wind.integration.im.session;

import com.wind.common.exception.AssertUtils;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.spi.WindImSessionService;
import com.wind.websocket.core.WindSessionConnectionPolicy;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketSession;
import com.wind.websocket.core.WindSocketSessionDescriptor;
import com.wind.websocket.core.WindSocketSessionManager;
import com.wind.websocket.core.WindSocketSessionStatus;
import com.wind.websocket.core.WindSocketSessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的 {@link WindSocketSessionManager} 实现类。
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
public class DefaultWindSocketSessionManager implements WindSocketSessionManager {

    /**
     * 本地内存缓存连接描述符
     * 该缓存存储连接ID对应的 WindSocketClientClientConnection 实例。
     * 注意：该缓存为本地进程内存，不适合多实例共享。
     */
    private final Map<String, WindSocketClientClientConnection> localConnectionDescriptorCache = new ConcurrentHashMap<>();

    private final RedissonClient redissonClient;

    private final WindImSessionService sessionService;


    /**
     * 创建一个新的 WebSocket 会话（WindSocketSession）
     *
     * @param sessionId 指定的会话 ID（可为 null，null 时自动生成）
     * @param name      会话名称（可为 null）
     * @param metadata  元数据，用于传递会话初始化所需的附加信息，不能为空
     *                  <p>
     *                  metadata 要求包含以下必要字段：
     *                  - Key: "sessionMember"
     *                  Value: Set<String>，表示该会话包含的用户 UID 集合（不可为空）
     *                  <p>
     *                  会校验会话是否已存在，若存在则抛出异常；否则创建会话并初始化。
     * @return 创建后的 WindSocketSession 实例
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WindSocketSession createSession(@Null String sessionId, @Null String name, @NotNull Map<String, Object> metadata) {
        String id = sessionService.createIfAbsent(sessionId, name, metadata);
        return buildDefaultWindSocketSession(id);
    }

    /**
     * 更新指定会话的名称（存储在 metadata 中）。
     *
     * @param sessionId      会话 ID
     * @param newSessionName 新名称（非空）
     */
    @Override
    public void updateSessionName(@NotBlank String sessionId, @NotBlank String newSessionName) {
        AssertUtils.hasText(sessionId, "sessionId cannot be empty");
        AssertUtils.hasText(newSessionName, "newSessionName cannot be empty");
        WindSocketSession session = getSession(sessionId);
        String sessionName = newSessionName.trim();
        Map<String, Object> metadata = new HashMap<>(Objects.requireNonNullElse(session.getMetadata(), Collections.emptyMap()));
        metadata.put(WindImConstants.SESSION_NAME_VARIABLE_NAME, sessionName);
        // 更新会话元数据
        sessionService.updateSessionMetadata(sessionId, newSessionName, metadata);
    }

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

