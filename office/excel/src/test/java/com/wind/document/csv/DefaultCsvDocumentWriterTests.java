package com.wind.document.csv;

import com.wind.office.excel.ExcelDocumentWriter;
import com.wind.office.excel.ExcelTestsUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * @author wuxp
 * @date 2025-10-15 13:35
 **/
class DefaultCsvDocumentWriterTests {

    @Test
    void testWrite() throws Exception {
        Path path = ExcelTestsUtils.getClasspathFilepath("test-csv-export.csv");
        ExcelDocumentWriter writer = DefaultCsvDocumentWriter.of(Files.newOutputStream(path), ExcelTestsUtils.User.class);
        Collection<ExcelTestsUtils.User> users = ExcelTestsUtils.mockUsers();
        writer.write(users);
        Assertions.assertTrue(Files.exists(path));
    }
}
