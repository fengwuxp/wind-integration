package com.wind.office.excel.export;

import com.wind.office.core.OfficeDocumentType;
import com.wind.office.excel.ExcelCellQuickBuilder;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author wuxp
 * @date 2025-01-14 14:15
 **/
class ExcelCellQuickBuilderTests {


    @Test
    void testForClass() {
        List<ExcelCellDescriptor> descriptors = ExcelCellQuickBuilder.forClass(Example.class);
        Assertions.assertEquals(9, descriptors.size());
    }


    @Data
    public static class Example {

        private Long id;

        @Schema(description = "姓名")
        private String name;

        @Schema(name = "Flag")
        private Boolean flag;

        @Schema(description = "F")
        private boolean f;

        @Schema(description = "标签")
        private String[] tags;

        private OfficeDocumentType type;

        private List<String> values;

        public boolean isA() {
            return false;
        }

        public String getExample() {
            return "";
        }
    }
}
