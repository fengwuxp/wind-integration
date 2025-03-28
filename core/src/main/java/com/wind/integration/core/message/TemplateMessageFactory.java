package com.wind.integration.core.message;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 模板消息工厂
 *
 * @author wuxp
 * @date 2023-09-28 15:31
 **/
public interface TemplateMessageFactory<T> {

    /**
     * 根据业务数据转换消息
     *
     * @param templateId     消息模板 id
     * @param source         主要的业务数据
     * @param extraVariables 额外的业务数据
     * @return 消息列表
     */
    @SuppressWarnings({"rawtypes", "unckeced"})
    Collection<MessageDefinition<?>> getMessages(String templateId, T source, Map<String, Object> extraVariables);

    /**
     * 根据业务数据转换消息
     *
     * @param templateId 消息模板 id
     * @param source     主要的业务数据
     * @return 消息列表
     */
    @SuppressWarnings({"rawtypes", "unckeced"})
    default Collection<MessageDefinition<?>> getMessages(String templateId, T source) {
        return getMessages(templateId, source, Collections.emptyMap());
    }

    /**
     * @return 模板消息工厂唯一标识
     */
    default String getName() {
        return getClass().getName();
    }
}
