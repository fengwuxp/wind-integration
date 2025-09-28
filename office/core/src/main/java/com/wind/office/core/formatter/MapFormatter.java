package com.wind.office.core.formatter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.Formatter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * 根据 map 进行值转换
 *
 * @author wuxp
 */
@Slf4j
public record MapFormatter<T>(Map<String, Object> dataSource) implements Formatter<T> {

    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public T parse(@Nullable String text, @Nullable Locale locale) {
        if (text != null) {
            for (Map.Entry<String, Object> entry : this.dataSource.entrySet()) {
                if (entry.getValue().equals(text)) {
                    return (T) entry.getKey();
                }
            }
        }
        return null;
    }

    @NonNull
    @Override
    public String print(@NonNull T value, @Nullable Locale locale) {
        return (String) this.dataSource.get(String.valueOf(value));
    }
}
