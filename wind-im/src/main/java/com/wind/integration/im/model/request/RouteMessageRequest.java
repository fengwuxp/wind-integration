package com.wind.integration.im.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.experimental.FieldNameConstants;
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 用于封装IM系统中的消息路由请求，支持泛型类型的消息内容传输。
 *
 * @param <T>                     消息内容类型
 * @param sessionId               会话 ID
 * @param payload                 消息内容
 * @param receiveUserId           接收方用户 ID
 * @param receiveClientDeviceType 接收方客户端设备类型
 * @param metadata                消息元数据
 * @author wuxp
 * @date 2025-12-12 11:03
 */
@FieldNameConstants
public record RouteMessageRequest<T>(@NonNull String sessionId,
                                     @NonNull T payload,
                                     @NonNull String receiveUserId,
                                     @NonNull String receiveClientDeviceType,
                                     @NonNull Map<String, Object> metadata) implements Serializable {
    @Serial
    private static final long serialVersionUID = -8864951965985845124L;

    @JsonCreator
    public RouteMessageRequest(
            @JsonProperty(Fields.sessionId) String sessionId,
            @JsonProperty(Fields.payload) T payload,
            @JsonProperty(Fields.receiveUserId) String receiveUserId,
            @JsonProperty(Fields.receiveClientDeviceType) String receiveClientDeviceType,
            @JsonProperty(Fields.receiveClientDeviceType) Map<String, Object> metadata
    ) {
        this.sessionId = sessionId;
        this.payload = payload;
        this.receiveUserId = receiveUserId;
        this.receiveClientDeviceType = receiveClientDeviceType;
        this.metadata = metadata;
    }
}
