package com.wind.office.excel.read;

import com.wind.office.excel.ExcelDocumentImportWriter;
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
        List<BasedFieldNameRowToObjectConverterTests.User> users = new ArrayList<>();
        DefaultEasyExcelDocumentReader reader = DefaultEasyExcelDocumentReader.of(BasedFieldNameRowToObjectConverterTests.User.class,
                (ExcelDocumentImportWriter<BasedFieldNameRowToObjectConverterTests.User>) users::addAll);
        try (InputStream inputStream = getClass().getResourceAsStream("/excel-read-test.xlsx")) {
            reader.read(inputStream, rows -> {

            });
        }
        Assertions.assertEquals(9000, users.size());
    }

}
