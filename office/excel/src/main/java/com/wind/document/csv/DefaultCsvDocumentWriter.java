package com.wind.document.csv;


import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.ExcelDocumentWriter;
import com.wind.office.excel.export.SpringExpressionObjectToRowConverter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * 基于 Apache Commons CSV 的文档 writer，流式写入 CSV 文件，自动写表头
 *
 * @author wuxp
 * @date 2025-10-15
 */
@Slf4j
public class DefaultCsvDocumentWriter implements ExcelDocumentWriter {

    private final CSVPrinter csvPrinter;

    private final SpringExpressionObjectToRowConverter rowConverter;

    private final boolean escapeFormulas;

    private DefaultCsvDocumentWriter(CSVPrinter csvPrinter, List<ExcelCellDescriptor> descriptors, boolean escapeFormulas) {
        this.csvPrinter = csvPrinter;
        this.rowConverter = new SpringExpressionObjectToRowConverter(descriptors);
        this.escapeFormulas = escapeFormulas;
    }

    public static DefaultCsvDocumentWriter of(OutputStream output, Class<?> objectType) {
        return of(output, ExcelCellQuickBuilder.forClass(objectType));
    }

    public static DefaultCsvDocumentWriter of(OutputStream output, List<ExcelCellDescriptor> descriptors) {
        CSVPrinter printer = buildCsvPrinter(output, descriptors);
        return new DefaultCsvDocumentWriter(printer, descriptors, false);
    }

    /**
     * 创建转义公式型单元格值的 CSV writer
     *
     * @param output     输出流
     * @param objectType 导出对象类型
     * @return CSV writer
     */
    public static DefaultCsvDocumentWriter ofEscapingFormulas(OutputStream output, Class<?> objectType) {
        return ofEscapingFormulas(output, ExcelCellQuickBuilder.forClass(objectType));
    }

    /**
     * 创建转义公式型单元格值的 CSV writer
     *
     * @param output      输出流
     * @param descriptors 单元格描述
     * @return CSV writer
     */
    public static DefaultCsvDocumentWriter ofEscapingFormulas(OutputStream output, List<ExcelCellDescriptor> descriptors) {
        CSVPrinter printer = buildCsvPrinter(output, descriptors);
        return new DefaultCsvDocumentWriter(printer, descriptors, true);
    }

    @Override
    public void write(Collection<?> rows) {
        try {
            for (Object row : rows) {
                List<String> values = rowConverter.convert(row);
                csvPrinter.printRecord(escapeFormulas
                        ? values.stream().map(DefaultCsvDocumentWriter::neutralizeFormula).toList()
                        : values);
            }
            csvPrinter.flush();
        } catch (IOException exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, "write csv data exception", exception);
        }
    }

    @Override
    public void finish() {
        try {
            csvPrinter.close(true);
        } catch (IOException exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, "finish write csv data exception", exception);
        }
    }

    private static CSVPrinter buildCsvPrinter(OutputStream output, List<ExcelCellDescriptor> descriptors) {
        try {
            CSVFormat format = CSVFormat.DEFAULT
                    .builder()
                    .setHeader(descriptors.stream().map(ExcelCellDescriptor::getTitle).toArray(String[]::new))
                    .setRecordSeparator(System.lineSeparator())
                    .get();
            return new CSVPrinter(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)), format);
        } catch (IOException exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, "create csv printer exception", exception);
        }
    }

    private static String neutralizeFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r', '\n' -> "'" + value;
            default -> value;
        };
    }
}
