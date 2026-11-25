package com.wind.integration.infrastructure.message;

import com.wind.common.annotations.VisibleForTesting;
import com.wind.common.exception.AssertUtils;
import com.wind.common.util.ExecutorServiceUtils;
import com.wind.integration.core.message.MessageDefinition;
import com.wind.integration.core.message.MessageSender;
import com.wind.integration.core.message.TemplateMessageDescriptor;
import com.wind.integration.core.message.TemplateMessageFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 根据模板描述符和业务数据发送通知。描述符指定的 {@link TemplateMessageFactory} 根据模板标识和变量生成消息集合，
 * 再由配置的 {@link MessageSender} 发送。适用于业务代码按通知场景发消息，例如根据订单数据发送邮件。
 *
 * <p>应用初始化时通过 {@link #of(MessageSender, Collection)} 配置发送器和名称唯一的工厂。
 * 再次调用会替换静态发送入口使用的配置。{@link #send(TemplateMessageDescriptor, Object, Map)} 在调用线程中执行，
 * {@link #asyncSend(TemplateMessageDescriptor, Object, Map)} 将同一发送流程提交到进程内线程池。</p>
 *
 * @author wuxp
 * @date 2023-10-09 15:51
 **/
@Slf4j
public class TemplateMessageSender {

    private static final ExecutorService EXECUTOR = ExecutorServiceUtils.custom("template-message-send-", 1, 4, 256);

    private static final AtomicReference<TemplateMessageSender> TEMPLATE_SENDER = new AtomicReference<>();

    private final MessageSender<MessageDefinition<?>> sender;

    /**
     * @key 工厂名称 {@link TemplateMessageFactory#getName()}
     * @value 工厂实例
     */
    private final Map<String, TemplateMessageFactory<?>> factories;

    private TemplateMessageSender(MessageSender<MessageDefinition<?>> sender, Collection<TemplateMessageFactory<?>> factories) {
        this.sender = sender;
        this.factories = factories.stream().collect(Collectors.toMap(TemplateMessageFactory::getName, Function.identity()));
        TEMPLATE_SENDER.set(this);
    }

    public static TemplateMessageSender of(MessageSender<MessageDefinition<?>> sender, Collection<TemplateMessageFactory<?>> factories) {
        return new TemplateMessageSender(sender, factories);
    }

    @VisibleForTesting
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sendTemplateMessage(TemplateMessageDescriptor descriptor, Object data, Map<String, Object> extraVariables) {
        TemplateMessageFactory factory = factories.get(descriptor.getFactoryName());
        AssertUtils.notNull(factory, "not found TemplateMessageFactory, name = {}", descriptor.getFactoryName());
        Collection<MessageDefinition<?>> messages = factory.getMessages(descriptor.getTemplateId(), data, extraVariables);
        sender.sendMessages(messages);
    }

    /**
     * 发送模板消息
     *
     * @param descriptor 模板消息标识
     * @param data       主要的业务数据
     */
    public static void send(TemplateMessageDescriptor descriptor, Object data) {
        send(descriptor, data, Collections.emptyMap());
    }

    /**
     * 异步发送模板消息
     *
     * @param descriptor 模板消息标识
     * @param data       主要的业务数据
     */
    public static void asyncSend(TemplateMessageDescriptor descriptor, Object data, Map<String, Object> extraVariables) {
        EXECUTOR.execute(() -> send(descriptor, data, extraVariables));
    }

    /**
     * 发送模板消息
     *
     * @param descriptor     模板消息标识
     * @param data           主要的业务数据
     * @param extraVariables 额外的业务数据
     */
    public static void send(TemplateMessageDescriptor descriptor, Object data, Map<String, Object> extraVariables) {
        AssertUtils.notNull(TEMPLATE_SENDER.get(), "TemplateMessageSender not initialize");
        TEMPLATE_SENDER.get().sendTemplateMessage(descriptor, data, extraVariables);
    }

}
