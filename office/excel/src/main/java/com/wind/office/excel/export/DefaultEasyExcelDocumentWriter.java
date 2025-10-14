package com.wind.office.excel.export;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.builder.ExcelWriterSheetBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.row.SimpleRowHeightStyleStrategy;
import com.wind.office.excel.ExcelDocumentWriter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 easyexcel 的 excel writer
 *
 * @author wuxp
 * @date 2024-01-03 13:19
 * @github https://github.com/alibaba/easyexcel
 **/
public class DefaultEasyExcelDocumentWriter implements ExcelDocumentWriter {

    /**
     * 单个 sheet 最大行数
     */
    private static final int EXCEL_SINGLE_SHEET_MAX_ROW_SIZE = 1048575;

    private final AtomicInteger sheetIndex = new AtomicInteger(0);

    private final AtomicInteger sheetRowCount = new AtomicInteger(0);

    private final AtomicReference<WriteSheet> currentSheet = new AtomicReference<>();

    private final ExcelWriter excelWriter;

    private final SpringExpressionRowDataFormatter formatter;

    private DefaultEasyExcelDocumentWriter(List<ExcelCellDescriptor> descriptors, ExcelWriter excelWriter) {
        this.excelWriter = excelWriter;
        this.formatter = new SpringExpressionRowDataFormatter(descriptors);
        buildNextSheet();
    }

    public static DefaultEasyExcelDocumentWriter of(OutputStream output, List<ExcelCellDescriptor> descriptors) {
        List<WriteHandler> handlers = Arrays.asList(
                new CustomHeadColumnWidthStyleStrategy(descriptors),
                new SimpleRowHeightStyleStrategy((short) 25, (short) 25));
        return of(output, descriptors, handlers);
    }

    public static DefaultEasyExcelDocumentWriter of(OutputStream output, List<ExcelCellDescriptor> descriptors, Collection<WriteHandler> handlers) {
        List<String> titles = descriptors.stream().map(ExcelCellDescriptor::getTitle).toList();
        ExcelWriterBuilder builder = EasyExcelFactory.write(output)
                .head(titles.stream().map(Collections::singletonList).toList())
                .needHead(true)
                .charset(StandardCharsets.UTF_8)
                .autoCloseStream(false);
        for (WriteHandler handler : handlers) {
            builder.registerWriteHandler(handler);
        }
        return new DefaultEasyExcelDocumentWriter(descriptors, builder.build());
    }

    @Override
    public void write(Collection<Object> rows) {
        if (sheetRowCount.get() + rows.size() >= EXCEL_SINGLE_SHEET_MAX_ROW_SIZE) {
            // 超过单个 sheet 最大行数
            buildNextSheet();
        }
        excelWriter.write(rows.stream().map(formatter::formatRows).toList(), currentSheet.get());
        sheetRowCount.set(sheetRowCount.get() + rows.size());
    }

    @Override
    public void finish() {
        excelWriter.finish();
    }

    private void buildNextSheet() {
        WriteSheet sheet = new ExcelWriterSheetBuilder(excelWriter)
                .sheetNo(sheetIndex.get())
                .sheetName("Sheet%d".formatted(sheetIndex.incrementAndGet()))
                .build();
        currentSheet.set(sheet);
        sheetRowCount.set(0);
    }

}
