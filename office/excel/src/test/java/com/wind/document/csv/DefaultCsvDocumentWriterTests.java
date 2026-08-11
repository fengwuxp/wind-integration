package com.wind.document.csv;

import com.wind.office.excel.ExcelDocumentWriter;
import com.wind.office.excel.ExcelTestsUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

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

    @Test
    void testWritePreservesLeadingCharactersByDefault() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelDocumentWriter writer = DefaultCsvDocumentWriter.of(output, ExcelTestsUtils.User.class);

        writer.write(List.of(
                List.of("=2+2", "18"),
                List.of("+8613800000000", "18"),
                List.of("-1", "18"),
                List.of("@name", "18")
        ));
        writer.finish();

        String csv = output.toString(StandardCharsets.UTF_8);
        Assertions.assertTrue(csv.contains("=2+2"));
        Assertions.assertTrue(csv.contains("+8613800000000"));
        Assertions.assertTrue(csv.contains("-1"));
        Assertions.assertTrue(csv.contains("@name"));
        Assertions.assertFalse(csv.contains("'=2+2"));
        Assertions.assertFalse(csv.contains("'+8613800000000"));
        Assertions.assertFalse(csv.contains("'-1"));
        Assertions.assertFalse(csv.contains("'@name"));
    }

    @Test
    void testWriteEscapesSpreadsheetFormulas() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelDocumentWriter writer = DefaultCsvDocumentWriter.ofEscapingFormulas(output, ExcelTestsUtils.User.class);

        writer.write(List.of(
                List.of("=2+2", "18"),
                List.of("+SUM(1,2)", "18"),
                List.of("-cmd", "18"),
                List.of("@SUM(1,2)", "18"),
                List.of("\t=2+2", "18")
        ));
        writer.finish();

        String csv = output.toString(StandardCharsets.UTF_8);
        Assertions.assertTrue(csv.contains("'=2+2"));
        Assertions.assertTrue(csv.contains("'+SUM(1,2)"));
        Assertions.assertTrue(csv.contains("'-cmd"));
        Assertions.assertTrue(csv.contains("'@SUM(1,2)"));
        Assertions.assertTrue(csv.contains("'\t=2+2"));
    }
}
