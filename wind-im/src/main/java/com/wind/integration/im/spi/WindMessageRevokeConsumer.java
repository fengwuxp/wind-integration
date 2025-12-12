package com.wind.integration.im.spi;

import com.wind.integration.im.model.ImmutableMessageRevokeCommand;

import java.util.function.Consumer;

/**
 * 消息撤回处理
 *
 * @author wuxp
 * @date 2025-12-12 15:01
 **/
public interface WindMessageRevokeConsumer extends Consumer<ImmutableMessageRevokeCommand> {
}
