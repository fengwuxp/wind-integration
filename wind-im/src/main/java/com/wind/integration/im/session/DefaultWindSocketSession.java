package com.wind.integration.im.session;

import com.wind.common.exception.AssertUtils;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.connection.DefaultWindSocketConnectionListener;
import com.wind.integration.im.connection.ImmutableSocketClientConnectionMetadata;
import com.wind.integration.im.connection.RemoteSocketRouteClientConnection;
import com.wind.integration.im.spi.WindImSessionService;
import com.wind.websocket.core.SocketClientConnectionDescriptor;
import com.wind.websocket.core.WindSessionConnectionPolicy;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketSession;
import com.wind.websocket.core.WindSocketSessionStatus;
import com.wind.websocket.core.WindSocketSessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 默认实现的 {@link WindSocketSession}，用于管理一个 WebSocket 会话中的所有用户连接。
 * <p>
 * 支持多种连接策略（如单设备、多设备、互踢）及用户在线状态、连接管理、消息广播等操作。
 * </p>
 * <p>
 * 核心数据结构：
 * <ul>
 *   <li>{@code userConnections} - 映射用户ID到其所有连接（支持多设备）</li>
 *   <li>{@code connections} - 所有连接的全量映射（连接ID -> 连接）</li>
 * </ul>
 * </p>
 *
 * @author wuxp
 * @date 2025-12-12 13:13
 **/
@Slf4j
public class DefaultWindSocketSession implements WindSocketSession {


    /**
     * Redis 中存储连接描述符相关缓存的 Key 模板
     */
    private static final String SOCKET_USER_CONNECTION_CACHE_KEY = "SOCKET_USER_CONNECTIONS";

    /**
     * 用户ID 到连接描述符列表的映射缓存（Redis Map结构）。
     * 用于维护每个用户当前活跃的连接信息，支持多端设备连接场景，可用于消息路由、连接状态检查等。
     *
     * @key 用户 ID
     * @value 该用户下所有连接列表
     * Redis Key 示例：SOCKET_USER_CONNECTIONS
     */
    private final RMap<String, List<SocketClientConnectionDescriptor>> userConnectionCache;

    /**
     * 本地 Socket 连接缓存
     *
     * @key 连接 ID
     * @value {@link WindSocketClientClientConnection} 实例
     */
    private final Map<String, WindSocketClientClientConnection> localConnectionCache;

    /**
     * 会话服务
     */
    private final WindImSessionService sessionService;

    /**
     * 会话 ID
     */
    private final String id;
    /**
     * 会话创建时间
     */
    private final LocalDateTime gmtCreate;
    /**
     * 会话状态
     */
    private final WindSocketSessionStatus status;
    /**
     * 连接策略（如单设备互踢、多设备共存等）
     */
    private final WindSessionConnectionPolicy connectionPolicy;
    /**
     * 会话类型（点对点、群组等）
     */
    private final WindSocketSessionType sessionType;
    /**
     * 元数据
     */
    private final Map<String, Object> metadata;

    /**
     * 构造函数。
     *
     * @param id               会话 ID
     * @param gmtCreate        创建时间
     * @param status           会话状态
     * @param connectionPolicy 连接策略
     * @param sessionType      会话类型
     * @param metadata         元数据
     */
    public DefaultWindSocketSession(@NotBlank String id, LocalDateTime gmtCreate,
                                    WindSocketSessionStatus status,
                                    WindSessionConnectionPolicy connectionPolicy,
                                    WindSocketSessionType sessionType,
                                    Map<String, Object> metadata,
                                    RedissonClient redissonClient,
                                    WindImSessionService sessionService,
                                    Map<String, WindSocketClientClientConnection> localConnectionCache
    ) {
        this.id = id;
        this.gmtCreate = gmtCreate;
        this.status = status;
        this.connectionPolicy = connectionPolicy;
        this.sessionType = sessionType;
        this.metadata = (metadata != null) ? metadata : new HashMap<>();
        this.sessionService = sessionService;
        this.userConnectionCache = redissonClient.getMap(SOCKET_USER_CONNECTION_CACHE_KEY);
        this.localConnectionCache = localConnectionCache;
    }

    /**
     * 将用户加入会话，可根据连接策略决定是否允许多个设备连接。
     *
     * @param userId     用户 UID
     * @param connection 连接信息，可为 null（用于预注册用户）
     * @see DefaultWindSocketConnectionListener#onConnect(com.corundumstudio.socketio.SocketIOClient)
     */
    @Override
    public void joinUser(@NotNull String userId, @Null WindSocketClientClientConnection connection) {
        if (connection == null) {
            return;
        }
        // 设置 Redis 用户ID -> 连接描述符集合
        List<SocketClientConnectionDescriptor> clientConnections = getConnectionDescriptors(userId);

        switch (connectionPolicy) {
            case MULTI_DEVICE:
                clientConnections.removeIf(conn -> Objects.equals(conn.getSessionId(), id) &&
                        Objects.equals(conn.getClientDeviceType(), connection.getClientDeviceType()));
                clientConnections.add(connection);
                break;
            case SINGLE_DEVICE_KICK_NEW:
                // 踢掉旧连接，接入新连接（相同设备类型 + 当前会话）
                clientConnections.stream()
                        .filter(conn -> Objects.equals(conn.getSessionId(), id) &&
                                Objects.equals(conn.getClientDeviceType(), connection.getClientDeviceType()))
                        .forEach(item -> {
                            WindSocketClientClientConnection oldConn = localConnectionCache.get(item.getId());
                            if (oldConn != null) {
                                oldConn.close();
                                localConnectionCache.remove(item.getId());
                            } else {
                                log.warn("SINGLE_DEVICE_KICK_NEW：未找到旧连接 connectionId = {}", item.getId());
                            }
                        });

                // 移除连接描述符列表中对应的旧连接
                clientConnections.removeIf(conn -> Objects.equals(conn.getSessionId(), id) &&
                        Objects.equals(conn.getClientDeviceType(), connection.getClientDeviceType()));

                // 加入新连接
                clientConnections.add(connection);

                break;
            case SINGLE_DEVICE_KICK_OLD:
                // 同设备类型互斥，不同设备并存，优先保留旧连接，拒绝新连接
                boolean hasSameDeviceConnection = clientConnections.stream()
                        .anyMatch(conn ->
                                Objects.equals(conn.getSessionId(), id) &&
                                        Objects.equals(conn.getClientDeviceType(), connection.getClientDeviceType()));

                if (hasSameDeviceConnection) {
                    // 有旧连接，不允许新连接加入，直接关闭新连接
                    connection.close();
                    return;
                }

                clientConnections.add(connection);
                break;
            default:
                clientConnections.add(connection);
                break;
        }

        // 设置 连接ID -> 连接描述符
        localConnectionCache.put(connection.getId(), connection);

        // 设置 Redis 用户ID -> 连接描述符集合 保存更新后的连接描述符
        putConnectionDescriptors(userId, clientConnections.stream().map(this::from).collect(Collectors.toList()));
    }

    @NotNull
    private List<SocketClientConnectionDescriptor> getConnectionDescriptors(@NotBlank String userId) {
        return Optional.ofNullable(userConnectionCache.get(userId)).orElseGet(CopyOnWriteArrayList::new);
    }

    /**
     * 保存用户连接描述符
     *
     * @param userId      用户 UID
     * @param connections 连接列表
     */
    private void putConnectionDescriptors(@NotBlank String userId, List<SocketClientConnectionDescriptor> connections) {
        if (CollectionUtils.isEmpty(connections)) {
            userConnectionCache.remove(userId);
        } else {
            userConnectionCache.put(userId, connections);
        }
    }

    /**
     * 将 WebSocket 客户端连接对象转换为可序列化的连接描述符。
     * <p>
     * 用于在分布式场景中将连接信息保存至远程缓存（如 Redis），
     * 或通过网络在节点间传输连接状态。
     *
     * @param conn WebSocket 客户端连接对象，不能为空
     * @return 对应的可序列化连接描述符对象
     */
    private ImmutableSocketClientConnectionMetadata from(SocketClientConnectionDescriptor conn) {
        return new ImmutableSocketClientConnectionMetadata(
                conn.getId(),
                conn.getSessionId(),
                conn.getMetadata()
        );
    }


    /**
     * 移除指定用户在当前会话下的所有连接，并清理相关缓存。
     * <p>
     * 该方法通常用于用户强制下线、会话关闭或异常清理等场景。
     *
     * @param userId 用户 ID
     */
    @Override
    public void removeUser(@NotBlank String userId) {
        AssertUtils.hasText(userId, "argument userId must not empty");

        // 获取用户的所有连接描述符
        List<SocketClientConnectionDescriptor> connectionDescriptors = getConnectionDescriptors(userId);
        Iterator<SocketClientConnectionDescriptor> iterator = connectionDescriptors.iterator();
        while (iterator.hasNext()) {
            SocketClientConnectionDescriptor descriptor = iterator.next();
            if (descriptor.getSessionId().equals(id)) {
                // 从本地连接缓存中移除连接
                WindSocketClientClientConnection remove = localConnectionCache.remove(descriptor.getId());
                if (remove != null) {
                    remove.close();
                } else {
                    log.warn("连接不存在，无法关闭 connectionId = {}", descriptor.getId());
                }
                // 删除用户连接描述符
                iterator.remove();
            }
        }
        // 保存更新后的连接描述符
        putConnectionDescriptors(userId, connectionDescriptors);
    }

    @Override
    public boolean containsUser(@NotBlank String userId) {
        AssertUtils.hasText(userId, "argument userId must not empty");
        return sessionService.sessionExistUser(id, userId);
    }

    /**
     * 根据连接 ID 离开连接，并从所属用户连接中移除。
     * <p>
     * 若该连接是用户在当前会话中的唯一连接，额外将该用户从会话用户缓存中移除。
     *
     * @param connectionId 连接 ID
     */
    @Override
    public void leaveConnection(@NotNull String connectionId) {
        AssertUtils.hasText(connectionId, "argument connectionId must not empty");

        // 获取连接描述符（包括 userId、sessionId 等元信息）
        WindSocketClientClientConnection remove = localConnectionCache.remove(connectionId);
        if (remove == null) {
            log.warn("leaveConnection：未找到连接 connectionId = {}", connectionId);
            return;
        }
        // 关闭连接
        remove.close();

        // 获取用户的所有连接描述符
        List<SocketClientConnectionDescriptor> connectionDescriptors = getConnectionDescriptors(remove.getUserId());
        // 线程安全地移除连接
        connectionDescriptors.removeIf(descriptor -> descriptor.getId().equals(connectionId));
        // 保存更新后的连接描述符
        putConnectionDescriptors(remove.getUserId(), connectionDescriptors);
    }


    /**
     * 获取当前会话下所有连接。
     * <p>
     * 方法会优先尝试从本地缓存中获取连接对象，
     * 若不存在，则根据 Redis 中的连接描述符信息构造远程代理连接。
     *
     * @return 不可修改的连接集合
     */
    @Override
    public @NotNull Collection<WindSocketClientClientConnection> getConnections() {
        // 获取会话里面的所有成员
        return sessionService.getSessionMembers(id).stream()
                .map(this::getUserConnections)
                .flatMap(Collection::stream)
                .toList();
    }

    /**
     * 提取远程节点地址
     *
     * @param connection 连接对象
     * @return 远程节点地址
     */
    @NotNull
    private String getNodeAddress(@NotNull SocketClientConnectionDescriptor connection) {
        Map<String, Object> metadata = Objects.requireNonNullElse(connection.getMetadata(), Collections.emptyMap());
        String nodeAddress = (String) metadata.get(WindImConstants.NODE_IP_ADDRESS_VARIABLE_NAME);
        AssertUtils.hasText(nodeAddress, "connection node address must not empty");
        return nodeAddress;
    }

    /**
     * 获取指定用户的所有连接。
     * 获取指定用户在当前会话中的所有连接对象。
     * 支持本地连接与远程连接自动识别与封装。
     *
     * @param userId 用户 ID
     * @return 用户连接列表
     */
    @Override
    public @NotNull List<WindSocketClientClientConnection> getUserConnections(@NotNull String userId) {
        AssertUtils.hasText(userId, "argument userId must not empty");
        List<WindSocketClientClientConnection> result = new ArrayList<>();
        // 获取成员的所有 连接描述符
        List<SocketClientConnectionDescriptor> connectionDescriptors = getConnectionDescriptors(userId);
        for (SocketClientConnectionDescriptor connectionDescriptor : connectionDescriptors) {
            // 获取连接描述符对应的会话 ID
            if (connectionDescriptor.getSessionId().equals(id)) {
                // 获取本地连接对象
                WindSocketClientClientConnection connection = localConnectionCache.get(connectionDescriptor.getId());
                // 存在则加入结果集
                // 构造远程连接代理对象并加入结果集
                result.add(Objects.requireNonNullElseGet(connection,
                        () -> new RemoteSocketRouteClientConnection(getNodeAddress(connectionDescriptor), connectionDescriptor.getSessionId(),
                                connectionDescriptor.getMetadata())));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 获取当前所有在线用户的 ID。
     *
     * @return 当前会话中所有用户 ID 的不可变集合，若无用户则返回空集合。
     */
    @Override
    public @NotNull Collection<String> getUserIds() {
        // 获取会话里所有连接
        Collection<WindSocketClientClientConnection> connections = getConnections();
        // 获取所有用户 ID
        return connections.stream().map(WindSocketClientClientConnection::getUserId).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 判断用户是否在线（有存活连接）。
     *
     * @param userId 用户 ID
     * @return 是否在线
     */
    @Override
    public boolean isUserOnline(@NotNull String userId) {
        AssertUtils.hasText(userId, "argument userId must not empty");

        // 获取用户所有连接
        List<WindSocketClientClientConnection> connections = getUserConnections(userId);
        // 判断是否有存活连接
        return connections.stream().anyMatch(WindSocketClientClientConnection::isAlive);
    }

    /**
     * 向会话中所有用户广播消息，排除指定用户。
     *
     * @param payload         消息内容
     * @param excludedUserIds 不发送消息的用户列表
     */
    @Override
    public CompletableFuture<Void> broadcast(@NotNull Object payload, @NotNull Collection<String> excludedUserIds) {
        AssertUtils.notNull(payload, "argument payload must not");
        AssertUtils.notNull(excludedUserIds, "argument excludedUserIds must not nuull");
        Collection<WindSocketClientClientConnection> connections = getConnections();

        // 为每个符合条件的连接创建一个 CompletableFuture
        return CompletableFuture.allOf(connections.stream()
                .filter(conn -> !excludedUserIds.contains(conn.getUserId()))
                .map(conn -> conn.send(payload))
                .toArray(CompletableFuture[]::new));
    }

    @Override
    public @NotBlank String getId() {
        return this.id;
    }

    @Override
    public @NotNull LocalDateTime getGmtCreate() {
        return this.gmtCreate;
    }

    @Override
    public WindSocketSessionStatus getStatus() {
        return this.status;
    }

    @Override
    public WindSocketSessionType getSessionType() {
        return this.sessionType;
    }

    @Override
    @NotNull
    public Map<String, Object> getMetadata() {
        return this.metadata;
    }
}
