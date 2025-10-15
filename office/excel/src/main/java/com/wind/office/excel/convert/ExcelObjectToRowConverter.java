package com.wind.office.excel.convert;

import org.springframework.core.convert.converter.Converter;

import java.util.List;

/**
 * 将 Java 对象转为 excel 行数据
 *
 * @author wuxp
 * @date 2025-10-15 10:27
 **/
public interface ExcelObjectToRowConverter extends Converter<Object, List<String>> {

}
