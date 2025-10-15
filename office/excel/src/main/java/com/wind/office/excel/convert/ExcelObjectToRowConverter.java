package com.wind.office.excel.convert;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;

import java.util.List;

/**
 * excel 行数据转换器
 *
 * @author wuxp
 * @date 2025-10-15 10:27
 **/
public interface ExcelObjectToRowConverter extends Converter<Object, List<String>> {

    /**
     * 转换对象为 excel 行数据
     *
     * @param data 数据对象
     * @return 行数据
     */
    List<String> convert(@NotNull Object data);
}
