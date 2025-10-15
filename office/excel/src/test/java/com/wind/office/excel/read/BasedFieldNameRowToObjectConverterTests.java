package com.wind.office.excel.read;

import com.wind.office.excel.ExcelTestsUtils;
import com.wind.office.excel.convert.ExcelRowToObjectConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author wuxp
 * @date 2025-10-15 09:47
 **/
class BasedFieldNameRowToObjectConverterTests {


    @Test
    void testParse() {
        ExcelRowToObjectConverter<ExcelTestsUtils.User> parser = BasedFieldNameRowToObjectConverter.of(ExcelTestsUtils.User.class);
        ExcelTestsUtils.User user = parser.convert(List.of("张三", "18"));
        Assertions.assertNotNull(user);
        Assertions.assertEquals("张三", user.getName());
    }


}
