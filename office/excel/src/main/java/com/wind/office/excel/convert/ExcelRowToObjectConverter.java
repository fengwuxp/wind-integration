package com.wind.office.excel.convert;

import org.springframework.core.convert.converter.Converter;

import java.util.List;

/**
 * 转换 excel 行数据为对象
 *
 * @author wuxp
 * @date 2025-10-15 10:27
 **/
public interface ExcelRowToObjectConverter<E> extends Converter<List<String>, E> {

}
