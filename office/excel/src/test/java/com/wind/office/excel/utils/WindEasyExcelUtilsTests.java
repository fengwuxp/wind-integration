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
class WindEasyExcelUtilsTests {


    @Test
    void testReadExcel() {
        InputStream inputStream = getClass().getResourceAsStream("/%s".formatted("excel-read-test.xlsx"));
        List<ExcelTestsUtils.User> users = WindEasyExcelUtils.readAsObject(inputStream, ExcelTestsUtils.User.class);
        Assertions.assertEquals(9000, users.size());
    }

    @Test
    void testReadAllExcelWithEasy() {
        InputStream inputStream = getClass().getResourceAsStream("/%s".formatted("excel-read-test.xlsx"));
        List<ExcelTestsUtils.User> users = WindEasyExcelUtils.readAll(inputStream, ExcelTestsUtils.User.class);
        Assertions.assertEquals(9000, users.size());
    }

    @Test
    void testReadSheet0ExcelWithEasy() {
        InputStream inputStream = getClass().getResourceAsStream("/%s".formatted("excel-read-test.xlsx"));
        List<ExcelTestsUtils.User> users = WindEasyExcelUtils.read(inputStream, 0, ExcelTestsUtils.User.class);
        Assertions.assertEquals(3000, users.size());
    }

    @Test
    void testWriteExcel() throws Exception {
        Path filepath = ExcelTestsUtils.getClasspathFilepath("excel_utils_write.xlsx");
        WindEasyExcelUtils.write(Files.newOutputStream(filepath), new ArrayList<>(ExcelTestsUtils.mockUsers()));
        Assertions.assertTrue(Files.exists(filepath));
    }

    @Test
    void testWriteExcelWithEasy() throws Exception {
        Path filepath = ExcelTestsUtils.getClasspathFilepath("excel_utils_write.xlsx");
        WindEasyExcelUtils.write(Files.newOutputStream(filepath), new ArrayList<>(ExcelTestsUtils.mockUsers()), ExcelTestsUtils.User.class);
        Assertions.assertTrue(Files.exists(filepath));
    }
}
