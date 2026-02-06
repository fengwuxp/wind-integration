package com.wind.integration.tag;

import org.jspecify.annotations.NonNull;

/**
 * 不可变标签
 *
 * @author wuxp
 * @date 2026-02-06 13:30
 **/
record ImmutableTag(String name, String value) implements WindTag {

    @Override
    public @NonNull String getName() {
        return name();
    }

    @Override
    public @NonNull String getValue() {
        return value();
    }
}