package com.wind.office.excel.utils;

import com.wind.office.excel.ExcelTestsUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxp
 * @date 2025-10-16 17:09
 **/
class ExcelDocumentUtilsTests {


    @Test
    void testReadExcel() {
        InputStream inputStream = getClass().getResourceAsStream("/%s".formatted("excel-read-test.xlsx"));
        List<ExcelTestsUtils.User> users = ExcelDocumentUtils.readAsObject(inputStream, ExcelTestsUtils.User.class);
        Assertions.assertEquals(9000, users.size());
    }

    @Test
    void testWriteExcel() throws Exception {
        Path filepath = ExcelTestsUtils.getClasspathFilepath("excel_utils_write.xlsx");
        ExcelDocumentUtils.writeDocument(Files.newOutputStream(filepath), new ArrayList<>(ExcelTestsUtils.mockUsers()));
        Assertions.assertTrue(Files.exists(filepath));
    }
}
