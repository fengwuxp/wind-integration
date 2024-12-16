package com.wind.integration;

import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author fanqingwei
 * @since 2023-12-28
 */
public final class PodamUtils {

    private static final PodamFactory FACTORY = new PodamFactoryImpl();

    public static <T> T manufacturePojo(Class<T> clazz) {
        return FACTORY.manufacturePojo(clazz);
    }

    public static <T> List<T> manufacturePojo(Class<T> clazz, Integer size) {
        return IntStream.range(0, size)
                .mapToObj(i -> FACTORY.manufacturePojo(clazz))
                .collect(Collectors.toList());
    }

    public static <T> T manufacturePojo(Class<T> clazz, Type... types) {
        return FACTORY.manufacturePojo(clazz, types);
    }
}
