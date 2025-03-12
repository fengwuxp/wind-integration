package com.wind.integration.core.message;

import lombok.AllArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author wuxp
 * @date 2023-09-28 15:35
 **/
@AllArgsConstructor
public class CompositeMessageSender implements MessageSender<MessageDefinition<?>> {

    private final Collection<MessageSender<? extends MessageDefinition<?>>> delegates;

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
