package com.wind.office.excel.read;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.ExcelDocumentImportWriter;
import com.wind.office.excel.ExcelDocumentReadListener;
import com.wind.office.excel.ExcelDocumentReader;
import com.wind.office.excel.convert.ExcelRowToObjectConverter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 easyexcel 的 excel reader
 *
 * @author wuxp
 * @date 2025-10-15 09:11
 **/
@Slf4j
@SuppressWarnings("rawtypes")
public record DefaultEasyExcelDocumentReader(ExcelRowToObjectConverter<?> converter, ExcelDocumentImportWriter writer) implements ExcelDocumentReader {

    @Override
    public void read(InputStreamSource source, ExcelDocumentReadListener listener) {
        try (InputStream input = source.getInputStream()) {
            EasyExcelFactory.read(input)
                    .headRowNumber(1)
                    .ignoreEmptyRow(true)
                    .autoCloseStream(true)
                    .charset(StandardCharsets.UTF_8)
                    .registerReadListener(new BatchWriteListener(writer, converter::convert, listener))
                    .doReadAll();
        } catch (Exception exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, "read excel data exception", exception);
        }
    }

    @SuppressWarnings({"rawtypes"})
    public static DefaultEasyExcelDocumentReader of(Class<?> objectType, List<ExcelCellDescriptor> descriptors, ExcelDocumentImportWriter writer) {
        return new DefaultEasyExcelDocumentReader(BasedFieldNameRowToObjectConverter.of(objectType, descriptors), writer);
    }

    @SuppressWarnings("rawtypes")
    public static DefaultEasyExcelDocumentReader of(Class<?> objectType, ExcelDocumentImportWriter writer) {
        List<ExcelCellDescriptor> descriptors = ExcelCellQuickBuilder.forClass(objectType);
        return of(objectType, descriptors, writer);
    }

    @AllArgsConstructor
    private static class BatchWriteListener extends AnalysisEventListener<LinkedHashMap<Integer, String>> {

        private static final int BATCH_SIZE = 1000;

        private final ExcelDocumentImportWriter<Object> writer;

        private final ExcelRowToObjectConverter<Object> converter;

        private final ExcelDocumentReadListener listener;

        private final List<Object> caches = new ArrayList<>(BATCH_SIZE);

        private final AtomicInteger totalCount = new AtomicInteger(0);

        @Override
        public void invoke(LinkedHashMap<Integer, String> data, AnalysisContext context) {
            List<String> rowCellValues = new ArrayList<>();
            data.forEach((index, value) -> rowCellValues.add(value));
            try {
                caches.add(converter.convert(rowCellValues));
            } catch (Exception exception) {
                log.error("excel row convert exception, row = {}", rowCellValues, exception);
                listener.onException(rowCellValues, exception);
                return;
            }
            totalCount.incrementAndGet();
            if (caches.size() >= BATCH_SIZE) {
                writeRows();
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            writeRows();
            listener.onFinish();
            log.info("excel sheet read completed, total size  = {}", totalCount.get());
        }

        private void writeRows() {
            if (caches.isEmpty()) {
                return;
            }
            writer.write(caches);
            listener.onWrite(caches);
            caches.clear();
        }
    }
}
