package com.wind.integration.core.message.definition;

import com.wind.integration.core.message.MessageDefinition;
import com.wind.integration.core.message.WindSendMessageConstants;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * 简单的邮件消息
 *
 * @author wuxp
 * @date 2025-12-11 16:48
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimpleEmailMessage implements MessageDefinition<String> {

    private String title;

    private String message;

    private String format = WindSendMessageConstants.TEXT_FORMAT;

    private Set<String> receivers;

    private final Map<String, Object> metadata = new HashMap<>();

    public static SimpleEmailMessage text(String title, String message, Set<String> receivers) {
        return new SimpleEmailMessage(title, message, WindSendMessageConstants.TEXT_FORMAT, receivers);
    }

    public static SimpleEmailMessage html(String title, String message, Set<String> receivers) {
        return new SimpleEmailMessage(title, message, WindSendMessageConstants.HTML_FORMAT, receivers);
    }

    public boolean isText() {
        return WindSendMessageConstants.TEXT_FORMAT.equalsIgnoreCase(format);
    }

    public boolean isHtml() {
        return WindSendMessageConstants.HTML_FORMAT.equals(format);
    }

    @Override
    public @NotNull String title() {
        return title;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public @NotNull String format() {
        return format;
    }

    @Override
    public @NotNull Set<String> receivers() {
        return receivers;
    }

    public void markHtml() {
        this.setFormat(WindSendMessageConstants.HTML_FORMAT);
    }

}
