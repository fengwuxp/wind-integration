package com.wind.office.excel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author wuxp
 * @date 2025-10-15 13:39
 **/
public abstract class ExcelTestsUtils {

    public static @NotNull Path getClasspathFilepath(String filename) throws URISyntaxException, IOException {
        URL baseUrl = Objects.requireNonNull(ExcelTestsUtils.class.getResource("/"));
        Path filepath = Paths.get(Paths.get(baseUrl.toURI()).toString(), filename);
        Files.deleteIfExists(filepath);
        return filepath;
    }

    public static User mockUser() {
        return new User("张三", 18);
    }

    public static Collection<User> mockUsers() {
        return List.of(
                new User("张三", 18),
                new User("李四", 25)
        );
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class User {

        @Schema(name = "姓名")
        private String name;

        private Integer age;

    }
}
