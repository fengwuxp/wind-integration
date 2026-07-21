package com.wind.integration.application;

import com.wind.common.util.ServiceInfoUtils;
import com.wind.integration.core.application.CurrentApplicationMatcher;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * 默认当前运行应用名称匹配器
 *
 * @author wuxp
 * @date 2026-07-21 14:00
 **/
public class DefaultCurrentApplicationMatcher implements CurrentApplicationMatcher {

    @Override
    public boolean matches(@NonNull String expectedApplicationName) {
        return Objects.equals(expectedApplicationName, ServiceInfoUtils.getApplicationName());
    }
}
