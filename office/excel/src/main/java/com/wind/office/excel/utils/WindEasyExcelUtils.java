package com.wind.office.excel.utils;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.export.DefaultEasyExcelDocumentWriter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import com.wind.office.excel.read.DefaultEasyExcelDocumentReader;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * wind excel 工具类
 *
 * @author wuxp
 * @date 2025-10-24 09:26
 **/
@Slf4j
public final class WindEasyExcelUtils {

    private WindEasyExcelUtils() {
        throw new AssertionError();
    }

    /**
     * 读取 excel的 所有 sheet 数据
     *
     * @param source     excel 文档输入流
     * @param objectType 对象类型
     * @param <T>        对象类型
     * @return 对象列表
     */
    public static <T> List<T> readAsObject(@NotNull InputStreamSource source, @NotNull Class<T> objectType) {
        return readAsObject(source, objectType, ExcelCellQuickBuilder.forClass(objectType));
    }

    public static <T> List<T> readAsObject(@NotNull InputStream input, @NotNull Class<T> objectType) {
        return readAsObject(() -> input, objectType, ExcelCellQuickBuilder.forClass(objectType));
    }

    public static <T> List<T> readAsObject(@NotNull InputStream input, @NotNull Class<T> objectType, @NotEmpty List<ExcelCellDescriptor> descriptors) {
        return readAsObject(() -> input, objectType, descriptors);
    }

    /**
     * 读取 excel的 所有 sheet 数据
     *
     * @param source      excel 文档输入流来源
     * @param objectType  对象类型
     * @param descriptors excel 文档描述符
     * @param <T>         对象类型
     * @return 列表
     */
    public static <T> List<T> readAsObject(@NotNull InputStreamSource source, @NotNull Class<T> objectType, @NotEmpty List<ExcelCellDescriptor> descriptors) {
        AssertUtils.notNull(source, "argument source must not null");
        AssertUtils.notNull(objectType, "argument objectType must not null");
        AssertUtils.notEmpty(descriptors, "argument descriptors must not null");
        List<T> result = new ArrayList<>();
        DefaultEasyExcelDocumentReader.of(objectType, descriptors, result::addAll).read(source, rows -> {
        });
        return result;
    }

    /**
     * 读取 excel的 所有 sheet 数据
     *
     * @param source 输入流来源
     * @param clazz  对象类类型
     * @return List<Object>
     */
    @NotEmpty
    public static <T> List<T> readAll(@NotNull InputStreamSource source, Class<T> clazz) {
        try (InputStream input = source.getInputStream()) {
            ReadDataListener<T> listener = new ReadDataListener<>();
            EasyExcelFactory.read(input, clazz, listener).ignoreEmptyRow(true)
                    .doReadAll();
            return listener.getRecords();
        } catch (Exception exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "读取 Excel 文件失败，请检查文件", exception);
        }
    }

    /**
     * 读取 excel的 所有 sheet 数据
     *
     * @param in    输入流
     * @param clazz 对象类类型
     * @return List<Object>
     */
    @NotEmpty
    public static <T> List<T> readAll(@NotNull InputStream in, Class<T> clazz) {
        return readAll(() -> in, clazz);
    }

    /**
     * 读取 excel 的指定 sheet 数据
     *
     * @param in      输入流
     * @param sheetNo sheet序号, 从 0 开始
     * @param clazz   对象类类型
     * @return List<Object>
     */
    @NotEmpty
    public static <T> List<T> read(InputStream in, int sheetNo, Class<T> clazz) {
        return read(() -> in, sheetNo, clazz);
    }

    /**
     * 读取 excel 的指定 sheet 数据
     *
     * @param source  输入流来源
     * @param sheetNo sheet序号，从 0 开始
     * @param clazz   对象类类型
     * @return List<Object>
     */
    @NotEmpty
    public static <T> List<T> read(InputStreamSource source, int sheetNo, Class<T> clazz) {
        return read(source, sheetNo, 1, clazz);
    }

    /**
     * 读取 excel 的指定 sheet 数据
     *
     * @param source        输入流来源
     * @param sheetNo       sheet序号, 从 0 开始
     * @param headRowNumber 表头行数
     * @param clazz         对象类类型
     * @return List<Object>
     */
    @NotEmpty
    public static <T> List<T> read(InputStreamSource source, int sheetNo, int headRowNumber, Class<T> clazz) {
        try (InputStream input = source.getInputStream()) {
            ReadDataListener<T> listener = new ReadDataListener<>();
            // 注意：EasyExcel 会在读取完成后自动关闭流
            EasyExcelFactory.read(source.getInputStream(), clazz, listener).ignoreEmptyRow(true)
                    .sheet(sheetNo).headRowNumber(headRowNumber).doRead();
            return listener.getRecords();
        } catch (Exception exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "读取 Excel 文件失败，请检查文件", exception);
        }
    }

    /**
     * 通过输出流将数据写入 excel
     *
     * @param output  输出流
     * @param records 数据列表
     * @param clazz   数据类类型
     */
    public static void write(OutputStream output, List<?> records, Class<?> clazz) {
        write(output, "Sheet1", records, clazz);
    }

    /**
     * 通过输出流将数据写入 excel
     *
     * @param output    输出流
     * @param sheetName sheet名
     * @param records   数据列表
     * @param clazz     数据类类型
     */
    @SneakyThrows
    public static void write(OutputStream output, String sheetName, List<?> records, Class<?> clazz) {
        EasyExcelFactory.write(output, clazz).sheet(sheetName).doWrite(records);
    }

    /**
     * 通过文件路径流将数据写入 excel
     *
     * @param filename  文件名
     * @param sheetName sheet名
     * @param records   数据列表
     * @param clazz     数据类类型
     */
    @SneakyThrows
    public static void write(String filename, String sheetName, List<?> records, Class<?> clazz) {
        EasyExcelFactory.write(filename, clazz).sheet(sheetName).doWrite(records);
    }

    /**
     * 写入 excel 文档
     *
     * @param output  输出流
     * @param records 行数据
     */
    public static void write(@NotNull OutputStream output, List<?> records) {
        AssertUtils.notEmpty(records, "argument records must not empty");
        write(output, ExcelCellQuickBuilder.forClass(records.getFirst().getClass()), records);
    }

    /**
     * 写入 excel 文档
     *
     * @param output      输出流
     * @param descriptors excel 文档描述符
     * @param records     行数据
     */
    public static void write(@NotNull OutputStream output, @NotEmpty List<ExcelCellDescriptor> descriptors, @NotEmpty List<?> records) {
        AssertUtils.notNull(output, "argument output must not null");
        AssertUtils.notEmpty(descriptors, "argument records must not empty");
        DefaultEasyExcelDocumentWriter.of(output, descriptors).write(records);
    }


    @Getter
    @EqualsAndHashCode(callSuper = true)
    private static class ReadDataListener<T> extends AnalysisEventListener<T> {
        /**
         * 缓存的数据列表
         */
        private final List<T> records = new ArrayList<>();

        @Override
        public void invoke(T data, AnalysisContext analysisContext) {
            records.add(data);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext analysisContext) {
            log.info("excel sheet read completed, total size  = {}", records.size());
        }
    }
}
