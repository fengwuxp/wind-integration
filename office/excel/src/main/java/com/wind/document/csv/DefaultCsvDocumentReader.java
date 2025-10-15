package com.wind.document.csv;


import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.ExcelDocumentImportWriter;
import com.wind.office.excel.ExcelDocumentReadListener;
import com.wind.office.excel.ExcelDocumentReader;
import com.wind.office.excel.convert.ExcelRowToObjectConverter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import com.wind.office.excel.read.BasedFieldNameRowToObjectConverter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Apache Commons CSV 的 CSV reader
 * 功能特性：
 * - 支持流式批量读取
 * - 支持字段名到对象的自动映射（通过 ExcelRowToObjectConverter）
 *
 * @author wuxp
 * @date 2025-10-15
 */
@Slf4j
@SuppressWarnings({"rawtypes"})
public record DefaultCsvDocumentReader(ExcelRowToObjectConverter<?> converter, ExcelDocumentImportWriter writer) implements ExcelDocumentReader {

    @Override
    public void read(InputStream input, ExcelDocumentReadListener listener) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            CSVFormat format = CSVFormat.DEFAULT
                    .builder()
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .get();
            try (CSVParser parser = CSVParser.parse(reader, format)) {
                BatchWriteHandler handler = new BatchWriteHandler(writer, converter::convert, listener);
                boolean isFirst = true;
                for (CSVRecord rowCellValues : parser) {
                    if (!isFirst) {
                        // 手动跳过第一行（header）
                        List<String> row = new ArrayList<>();
                        rowCellValues.forEach(row::add);
                        handler.onRow(row);
                    } else {
                        isFirst = false;
                    }
                }
                handler.finish();
            }
        } catch (IOException e) {
            throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, "read csv data exception", e);
        }
    }

    @SuppressWarnings("rawtypes")
    public static DefaultCsvDocumentReader of(Class<?> objectType, List<ExcelCellDescriptor> descriptors, ExcelDocumentImportWriter writer) {
        return new DefaultCsvDocumentReader(BasedFieldNameRowToObjectConverter.of(objectType, descriptors), writer);
    }

    @SuppressWarnings("rawtypes")
    public static DefaultCsvDocumentReader of(Class<?> objectType, ExcelDocumentImportWriter writer) {
        List<ExcelCellDescriptor> descriptors = ExcelCellQuickBuilder.forClass(objectType);
        return of(objectType, descriptors, writer);
    }

    @AllArgsConstructor
    private static class BatchWriteHandler {

        private static final int BATCH_SIZE = 1000;

        private final ExcelDocumentImportWriter<Object> writer;

        private final ExcelRowToObjectConverter<Object> converter;

        private final ExcelDocumentReadListener listener;

        private final List<Object> caches = new ArrayList<>(BATCH_SIZE);

        private final AtomicInteger totalCount = new AtomicInteger(0);

        void onRow(List<String> data) {
            try {
                caches.add(converter.convert(data));
            } catch (Exception exception) {
                log.error("CSV row convert exception, row = {}", data, exception);
                listener.onException(data, exception);
                return;
            }
            totalCount.incrementAndGet();
            if (caches.size() >= BATCH_SIZE) {
                flushCaches();
            }
        }

        void finish() {
            flushCaches();
            listener.onFinish();
            log.info("CSV read completed, total size = {}", totalCount.get());
        }

        private void flushCaches() {
            if (caches.isEmpty()) {
                return;
            }
            writer.write(caches);
            listener.onWrite(caches);
            caches.clear();
        }
    }
}
