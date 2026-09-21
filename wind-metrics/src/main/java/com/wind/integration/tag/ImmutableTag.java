package com.wind.integration.tag;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * 不可变标签
 *
 * @author wuxp
 * @date 2026-02-06 13:30
 **/
@NullMarked
record ImmutableTag(String name, String value) implements WindTag {

    ImmutableTag {
        Objects.requireNonNull(name, "Tag name must not be null");
        Objects.requireNonNull(value, "Tag value must not be null");
    }
}
