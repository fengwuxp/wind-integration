package com.wind.integration.infrastructure.message;

import com.wind.common.exception.BaseException;
import com.wind.integration.core.message.MessageDefinition;
import com.wind.integration.core.message.TemplateMessageDescriptor;
import com.wind.integration.core.message.TemplateMessageFactory;
import com.wind.integration.core.message.definition.SimpleEmailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Isolated
class TemplateMessageSenderTests {

    private final TemplateMessageFactory<String> orderEmails = (templateId, orderNumber, variables) -> {
        String body = orderNumber + " " + variables.getOrDefault("status", "created");
        return List.of(SimpleEmailMessage.text(templateId, body, Set.of("buyer@example.com")),
                SimpleEmailMessage.text(templateId, body, Set.of("seller@example.com")));
    };

    private AtomicReference<TemplateMessageSender> registration;

    private TemplateMessageSender previousSender;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void saveRegistration() {
        registration = (AtomicReference<TemplateMessageSender>) ReflectionTestUtils.getField(TemplateMessageSender.class, "TEMPLATE_SENDER");
        previousSender = registration.getAndSet(null);
    }

    @AfterEach
    void restoreRegistration() {
        registration.set(previousSender);
    }

    @Test
    void testSendMessagesFromSelectedFactory() {
        List<MessageDefinition<?>> delivered = new ArrayList<>();
        TemplateMessageFactory<String> otherFactory = (id, data, variables) -> List.of();
        TemplateMessageSender.of(delivered::add, List.of(otherFactory, orderEmails));

        TemplateMessageSender.send(new OrderUpdate(orderEmails.getName()), "order-17", Map.of("status", "shipped"));

        Assertions.assertEquals(List.of(
                SimpleEmailMessage.text("order-update", "order-17 shipped", Set.of("buyer@example.com")),
                SimpleEmailMessage.text("order-update", "order-17 shipped", Set.of("seller@example.com"))), delivered);
    }

    @Test
    void testAsyncSendDeliversOnAnotherThread() throws Exception {
        CompletableFuture<Thread> deliveryThread = new CompletableFuture<>();
        TemplateMessageSender.of(message -> deliveryThread.complete(Thread.currentThread()), List.of(orderEmails));

        TemplateMessageSender.asyncSend(new OrderUpdate(orderEmails.getName()), "order-18", Map.of());

        Assertions.assertNotSame(Thread.currentThread(), deliveryThread.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testSendWithUnknownFactory() {
        List<MessageDefinition<?>> delivered = new ArrayList<>();
        TemplateMessageSender.of(delivered::add, List.of(orderEmails));

        Assertions.assertThrows(BaseException.class, () -> TemplateMessageSender.send(new OrderUpdate("unknown"), "order-19"));
        Assertions.assertTrue(delivered.isEmpty());
    }

    @Test
    void testSendWithoutRegistration() {
        Assertions.assertThrows(BaseException.class, () -> TemplateMessageSender.send(new OrderUpdate(orderEmails.getName()), "order-20"));
    }

    private record OrderUpdate(String factoryName) implements TemplateMessageDescriptor {

        @Override
        public String getFactoryName() {
            return factoryName;
        }

        @Override
        public String getTemplateId() {
            return "order-update";
        }
    }
}
