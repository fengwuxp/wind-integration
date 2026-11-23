package com.wind.integration.core.message;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组合消息发送者
 *
 * @author wuxp
 * @date 2023-09-28 15:35
 **/
public record CompositeMessageSender(Collection<MessageSender<? extends MessageDefinition<?>>> delegates) implements MessageSender<MessageDefinition<?>> {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void sendMessage(MessageDefinition<?> message) {
        for (MessageSender delegate : delegates) {
            if (delegate.supports(message.getClass())) {
                delegate.sendMessage(message);
            }
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void sendMessages(Collection<MessageDefinition<?>> messages) {
        Map<Class<? extends MessageDefinition>, List<MessageDefinition>> classListMap =
                messages.stream().collect(Collectors.groupingBy(MessageDefinition::getClass));
        classListMap.forEach((k, v) -> {
            for (MessageSender delegate : delegates) {
                if (delegate.supports(k)) {
                    delegate.sendMessages(v);
                }
            }
        });
    }
}
