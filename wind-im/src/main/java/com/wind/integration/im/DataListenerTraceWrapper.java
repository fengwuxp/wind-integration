package com.wind.integration.im;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.DataListener;
import com.wind.trace.WindTracer;

/**
 * {@link DataListener} 包装器，用于在消息处理前后自动注入/清理追踪信息（traceId）。
 * <p>
 * 每次收到消息时：
 * <ul>
 *   <li>调用 {@link WindTracer#trace()} 生成唯一 traceId 并放入上下文</li>
 *   <li>执行真正的 DataListener 逻辑</li>
 *   <li>调用 {@link WindTracer#clear()} 清理上下文，防止内存泄漏或跨请求污染</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * exampleNamespace.addEventListener(
 *      "xx",
 *     ImmutableChatMessage.class,
 *     DataListenerTraceWrapper.wrap(dataListener)
 * );
 * </pre>
 *
 * @author wuxp
 * @date 2025-12-12 13:53
 **/
public record DataListenerTraceWrapper<T>(DataListener<T> delegate) implements DataListener<T> {

    @Override
    public void onData(SocketIOClient client, T data, AckRequest ackSender) throws Exception {
        try {
            // 在当前线程上下文生成 trackingId
            WindTracer.TRACER.trace();
            // 委托给原始 listener
            delegate.onData(client, data, ackSender);
        } finally {
            // 确保清理，避免 traceId 泄漏
            WindTracer.TRACER.clear();
        }
    }


    /**
     * 包装一个带追踪能力的监听器
     *
     * @param delegate 原始 DataListener
     */
    public static <T> DataListenerTraceWrapper<T> wrap(DataListener<T> delegate) {
        return new DataListenerTraceWrapper<>(delegate);
    }
}
