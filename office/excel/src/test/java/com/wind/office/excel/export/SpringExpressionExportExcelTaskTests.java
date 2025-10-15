package com.wind.office.excel.export;

import com.wind.document.csv.DefaultCsvDocumentWriter;
import com.wind.office.core.OfficeTaskState;
import com.wind.office.excel.ExcelDocumentWriter;
import com.wind.office.excel.ExcelTestsUtils;
import com.wind.office.excel.ExportExcelDataFetcher;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class SpringExpressionExportExcelTaskTests {


    @Test
    void testTaskByExcel() throws Exception {
        SpringExpressionExportExcelTask task = createExportTask("test.xlsx");
        task.run();
        Assertions.assertEquals(OfficeTaskState.COMPLETED, task.getState());
        Assertions.assertEquals(3000, task.getRowSize());
        Assertions.assertEquals(0, task.getFailedRowSize());
    }

    @Test
    void testTaskByCsv() throws Exception {
        SpringExpressionExportExcelTask task = createExportTask("test.csv");
        task.run();
        Assertions.assertEquals(OfficeTaskState.COMPLETED, task.getState());
        Assertions.assertEquals(3000, task.getRowSize());
        Assertions.assertEquals(0, task.getFailedRowSize());
    }

    private SpringExpressionExportExcelTask createExportTask(String filename) throws URISyntaxException, IOException {
        List<ExcelCellDescriptor> heads = Arrays.asList(
                mockExcelHead("name", 25),
                mockExcelHead("age", 10),
                mockExcelHead("sex", 5)
        );
        Path filepath = ExcelTestsUtils.getClasspathFilepath(filename);
        ExcelDocumentWriter writer = filename.endsWith(".xlsx") ? DefaultEasyExcelDocumentWriter.of(Files.newOutputStream(filepath), heads)
                : DefaultCsvDocumentWriter.of(Files.newOutputStream(filepath), heads);
        return new SpringExpressionExportExcelTask(ExportExcelTaskInfo.of(filename, writer), mockExcelDataFetcher());
    }

    private ExportExcelDataFetcher<Object> mockExcelDataFetcher() {
        return (page, size) -> {
            if (page >= 2) {
                return Collections.emptyList();
            }
            List<Object> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add(mockRowData());
            }
            return result;
        };
    }

    private static Map<String, String> mockRowData() {
        return Map.of("name", RandomStringUtils.secure().nextAlphanumeric(12),
                "age", RandomStringUtils.secure().nextNumeric(2),
                "sex", RandomStringUtils.secure().nextAlphanumeric(1));
    }

    private ExcelCellDescriptor mockExcelHead(String name, int width) {
        return ExcelCellDescriptor.builder(name, String.format("['%s']", name))
                .width(width)
                .printer((val, locale) -> String.valueOf(val))
                .build();
    }

}