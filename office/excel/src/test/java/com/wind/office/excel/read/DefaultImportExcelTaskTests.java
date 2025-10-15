package com.wind.office.excel.read;

import com.wind.office.core.OfficeTaskState;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.ExcelDocumentImportWriter;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author wuxp
 * @date 2025-10-15 13:02
 **/
class DefaultImportExcelTaskTests {

    private DefaultImportExcelTask task;

    @BeforeEach
    void createAndRunTask() {
        InputStream inputStream = getClass().getResourceAsStream("/excel-read-test.xlsx");
        List<ExcelCellDescriptor> descriptors = ExcelCellQuickBuilder.forClass(BasedFieldNameRowToObjectConverterTests.User.class);
        ImportExcelTaskInfo taskInfo = ImportExcelTaskInfo.of(descriptors, inputStream);
        List<BasedFieldNameRowToObjectConverterTests.User> users = new ArrayList<>();
        DefaultEasyExcelDocumentReader reader = DefaultEasyExcelDocumentReader.of(BasedFieldNameRowToObjectConverterTests.User.class,
                (ExcelDocumentImportWriter<BasedFieldNameRowToObjectConverterTests.User>) users::addAll);
        task = new DefaultImportExcelTask(taskInfo, reader);
        task.run();
        Assertions.assertEquals(9000, users.size());
        Assertions.assertEquals(users.size(), task.getRowSize());
    }

    @Test
    void testImportExcelTask() {
        Assertions.assertEquals(OfficeTaskState.COMPLETED, task.getState());
    }

    @Test
    void testMockExportFailureRowsTask() throws Exception {
        task.addFailedRows(List.of(
                List.of("zhans", "18", "mock error"),
                List.of("lis", "23")
        ));
        URL baseUrl = Objects.requireNonNull(DefaultImportExcelTaskTests.class.getResource("/"));
        Path filepath = Paths.get(Paths.get(baseUrl.toURI()).toString(), "import-failure-rows.xlsx");
        Files.deleteIfExists(filepath);
        task.writeFailedRows(Files.newOutputStream(filepath));
    }


}
