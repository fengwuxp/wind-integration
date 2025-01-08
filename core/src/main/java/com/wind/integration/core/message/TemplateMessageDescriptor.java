package com.wind.integration.core.message;

/**
 * 模板消息描述符，用于描述具体一个业务场景的消息
 * 建议使用枚举实现
 *
 * @author wuxp
 * @version TemplateMessageDescriptor.java, v 0.1 2023年03月22日 17:12 wuxp
 */
public interface TemplateMessageDescriptor {

    /**
     * 模板消息唯一标识
     *
     * @return 一般消息模板的配置编码
     */
    String getTemplateId();

    /**
     * 用于获取 {@link TemplateMessageFactory}
     *
     * @return 消息工厂名称（唯一标识）
     * @see TemplateMessageFactory#getName()
     */
    String getFactoryName();
}