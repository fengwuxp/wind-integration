package com.wind.integration.im.connection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.common.exception.BaseException;
import com.wind.websocket.core.SocketClientConnectionDescriptor;
import lombok.experimental.FieldNameConstants;

import java.util.Map;

/**
 * 一个可序列化的 Socket 客户端连接描述符实现类，用于标识客户端连接的唯一性，并存储其关联的元数据。
 *
 * @author wuxp
 * @date 2025-12-12 10:47
 **/
@FieldNameConstants
public class ImmutableSocketClientConnectionMetadata implements SocketClientConnectionDescriptor {

    /**
     * 客户端连接唯一标识
     */
    private final String id;

    /**
     * 所属会话的 ID，用于连接与会话的关联
     */
    private final String sessionId;

    /**
     * 存储连接相关的元数据信息，例如设备类型、IP 地址等
     */
    private final Map<String, Object> metadata;

    /**
     * 默认构造函数（反序列化需要）
     */
    ImmutableSocketClientConnectionMetadata() {
        this(null, null, null);
    }

    /**
     * 构造函数，初始化连接描述符的基本信息。
     * 支持 Jackson 自动反序列化
     *
     * @param id        连接 ID
     * @param sessionId 所属会话 ID
     * @param metadata  连接元数据
     */
    @JsonCreator
    public ImmutableSocketClientConnectionMetadata(
            @JsonProperty(Fields.id) String id,
            @JsonProperty(Fields.sessionId) String sessionId,
            @JsonProperty(Fields.metadata) Map<String, Object> metadata) {
        this.id = id;
        this.sessionId = sessionId;
        this.metadata = metadata;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 根据 key 获取指定类型的元数据值。
     *
     * @param key 元数据的键
     * @param <T> 期望的返回类型
     * @return 对应的元数据值（可能为 null）
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key) {
        return (T) metadata.get(key);
    }

    @Override
    public <T> T requireMetadataValue(String key) {
        T val = getMetadataValue(key);
        if (val == null) {
            throw new BaseException("Missing metadata: " + key);
        }
        return val;
    }
}
