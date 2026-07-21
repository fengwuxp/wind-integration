package com.wind.integration.core.application;

import org.jspecify.annotations.NonNull;

/**
 * 判断当前运行应用是否与指定应用名称匹配。
 *
 * @author wuxp
 * @since 2026-07-21
 */
@FunctionalInterface
public interface CurrentApplicationMatcher {

    /**
     * @param applicationName 待匹配的应用名称
     * @return 当前运行应用是否匹配
     */
    boolean matches(@NonNull String applicationName);
}