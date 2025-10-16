package com.wind.office.excel.utils;

import com.wind.common.exception.AssertUtils;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.export.DefaultEasyExcelDocumentWriter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import com.wind.office.excel.read.DefaultEasyExcelDocumentReader;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxp
 * @date 2025-10-16 17:06
 **/
public final class ExcelDocumentUtils {

    private ExcelDocumentUtils() {
        throw new AssertionError();
    }

    /**
     * 读取 excel 文档为对象
     *
     * @param input      excel 文档输入流
     * @param objectType 对象类型
     * @param <T>        对象类型
     * @return 对象列表
     */
    public static <T> List<T> readAsObject(@NotNull InputStream input, @NotNull Class<T> objectType) {
        return readAsObject(input, objectType, ExcelCellQuickBuilder.forClass(objectType));
    }

    /**
     * 读取 excel 文档为对象
     *
     * @param input       excel 文档输入流
     * @param objectType  对象类型
     * @param descriptors excel 文档描述符
     * @param <T>         对象类型
     * @return 列表
     */
    public static <T> List<T> readAsObject(@NotNull InputStream input, @NotNull Class<T> objectType, @NotEmpty List<ExcelCellDescriptor> descriptors) {
        AssertUtils.notNull(input, "argument input must not null");
        AssertUtils.notNull(objectType, "argument objectType must not null");
        AssertUtils.notEmpty(descriptors, "argument descriptors must not null");
        List<T> result = new ArrayList<>();
        DefaultEasyExcelDocumentReader.of(objectType, descriptors, result::addAll).read(input, rows -> {
        });
        return result;
    }

    /**
     * 写入 excel 文档
     *
     * @param output 输出流
     * @param rows   行数据
     */
    public static void writeDocument(@NotNull OutputStream output, List<?> rows) {
        AssertUtils.notEmpty(rows, "argument rows must not empty");
        writeDocument(output, ExcelCellQuickBuilder.forClass(rows.getFirst().getClass()), rows);
    }

    /**
     * 写入 excel 文档
     *
     * @param output      输出流
     * @param descriptors excel 文档描述符
     * @param rows        行数据
     */
    public static void writeDocument(@NotNull OutputStream output, @NotEmpty List<ExcelCellDescriptor> descriptors, @NotEmpty List<?> rows) {
        AssertUtils.notNull(output, "argument output must not null");
        AssertUtils.notEmpty(descriptors, "argument rows must not empty");
        DefaultEasyExcelDocumentWriter.of(output, descriptors).write(rows);
    }
}
