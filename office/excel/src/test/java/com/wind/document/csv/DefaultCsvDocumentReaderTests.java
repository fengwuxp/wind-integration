package com.wind.document.csv;

import com.wind.office.excel.ExcelDocumentImportWriter;
import com.wind.office.excel.ExcelDocumentReader;
import com.wind.office.excel.ExcelTestsUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxp
 * @date 2025-10-15 14:00
 **/
class DefaultCsvDocumentReaderTests {

    @Test
    void testRead() throws Exception {
        List<ExcelTestsUtils.User> users = new ArrayList<>();
        ExcelDocumentReader reader = DefaultCsvDocumentReader.of(ExcelTestsUtils.User.class, (ExcelDocumentImportWriter<ExcelTestsUtils.User>) users::addAll);
        try (InputStream inputStream = getClass().getResourceAsStream("/csv-read-test.csv")) {
            reader.read(inputStream, rows -> {});
        }
        Assertions.assertEquals(2, users.size());
    }
}
