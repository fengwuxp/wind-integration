package com.wind.office.excel.read;

import com.wind.office.excel.convert.ExcelRowToObjectConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
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
        ExcelRowToObjectConverter<User> parser = BasedFieldNameRowToObjectConverter.of(User.class);
        User user = parser.convert(List.of("张三", "18"));
        Assertions.assertNotNull(user);
        Assertions.assertEquals("张三", user.getName());
    }


    @Data
    public static class User {

        @Schema(name = "姓名")
        private String name;

        private Integer age;

    }
}
