package com.wind.office.excel.export;

import com.wind.office.excel.metadata.ExcelCellDescriptor;
import lombok.Data;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author wuxp
 * @date 2025-10-14 17:38
 **/
class DefaultEasyExcelDocumentWriterTests {

    @Test
    void testWrite() throws Exception {
        writeExcel(2, 200);
    }

    @Test
    @Disabled
    void testBigData() throws Exception {
        writeExcel(500, 10000);
    }

    private void writeExcel(int pageCount, int pageSize) throws URISyntaxException, IOException {
        URL baseUrl = Objects.requireNonNull(SpringExpressionExportExcelTaskTests.class.getResource("/"));
        Path filepath = Paths.get(Paths.get(baseUrl.toURI()).toString(), "excel_write_%d.xlsx".formatted(pageCount));
        try (OutputStream out = new FileOutputStream(filepath.toFile())) {
            List<ExcelCellDescriptor> descriptors = List.of(
                    ExcelCellDescriptor.of("ID", "id"),
                    ExcelCellDescriptor.of("Name", "name")
            );

            DefaultEasyExcelDocumentWriter writer = DefaultEasyExcelDocumentWriter.of(out, descriptors);
            for (int i = 0; i < pageCount; i++) {
                List<Object> rows = new ArrayList<>();
                for (int j = 0; j < pageSize; j++) {
                    User row = new User();
                    int id = i * pageSize + j;
                    row.setId(id);
                    row.setName("name-%d".formatted(id));
                    rows.add(row);
                }
                writer.write(rows);
            }
            writer.finish();
        }
    }

    @Data
    public static class User {
        private int id;

        private String name;
    }
}
