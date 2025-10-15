package com.wind.office.excel.read;

import com.wind.office.core.OfficeTaskState;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.ExcelDocumentImportWriter;
import com.wind.office.excel.ExcelTestsUtils;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxp
 * @date 2025-10-15 13:02
 **/
class DefaultImportExcelTaskTests {


    @Test
    void testImportExcelTask() {
        DefaultImportExcelTask task = createAndRunTask("excel-read-test.xlsx");
        Assertions.assertEquals(OfficeTaskState.COMPLETED, task.getState());
        Assertions.assertEquals(9000, task.getRowSize());
    }

    @Test
    void testImportCsvTask() {
        DefaultImportExcelTask task = createAndRunTask("csv-read-test.csv");
        Assertions.assertEquals(OfficeTaskState.COMPLETED, task.getState());
        Assertions.assertEquals(2, task.getRowSize());
    }

    @Test
    void testMockExportFailureRowsTask() throws Exception {
        DefaultImportExcelTask task = createAndRunTask("excel-read-test.xlsx");
        List<Object> rows = List.of(
                List.of("zhans", "18", "mock error"),
                List.of("lis", "23")
        );
        task.addFailedRows(rows);
        task.writeFailedRows(Files.newOutputStream(ExcelTestsUtils.getClasspathFilepath("import-failure-rows.xlsx")));
        Assertions.assertEquals(rows.size(), task.getFailedRowSize());
    }

    private DefaultImportExcelTask createAndRunTask(String filename) {
        InputStream inputStream = getClass().getResourceAsStream("/%s".formatted(filename));
        List<ExcelCellDescriptor> descriptors = ExcelCellQuickBuilder.forClass(ExcelTestsUtils.User.class);
        ImportExcelTaskInfo taskInfo = ImportExcelTaskInfo.of(descriptors, inputStream);
        List<ExcelTestsUtils.User> users = new ArrayList<>();
        DefaultEasyExcelDocumentReader reader = DefaultEasyExcelDocumentReader.of(ExcelTestsUtils.User.class,
                (ExcelDocumentImportWriter<ExcelTestsUtils.User>) users::addAll);
        DefaultImportExcelTask result = new DefaultImportExcelTask(taskInfo, reader);
        result.run();
        Assertions.assertEquals(users.size(), result.getRowSize());
        return result;
    }


}
