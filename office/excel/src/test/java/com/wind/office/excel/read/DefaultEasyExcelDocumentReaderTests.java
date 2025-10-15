package com.wind.office.excel.read;

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
 * @date 2025-10-15 10:18
 **/
class DefaultEasyExcelDocumentReaderTests {

    @Test
    void testRead() throws Exception {
        List<ExcelTestsUtils.User> users = new ArrayList<>();
        ExcelDocumentReader reader = DefaultEasyExcelDocumentReader.of(ExcelTestsUtils.User.class, (ExcelDocumentImportWriter<ExcelTestsUtils.User>) users::addAll);
        try (InputStream inputStream = getClass().getResourceAsStream("/excel-read-test.xlsx")) {
            reader.read(inputStream, rows -> {

            });
        }
        Assertions.assertEquals(9000, users.size());
    }

}
