package com.wind.integration.metrics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 约束源码依赖方向：公共能力和查询/物化合同可以在不引用 DSL 实现的情况下使用。
 *
 * @author wuxp
 * @since 2026-09-15
 */
class MetricArchitectureDependencyTests {

    @Test
    void testPublicCapabilitiesAndJsonSupportDoNotDependOnDslOrJdbc() throws IOException {
        Path root = Path.of("src/main/java/com/wind/integration/metrics");
        Pattern implementationDependency = Pattern.compile("\\bcom\\.wind\\.integration\\.metrics\\.(dsl|jdbc)\\.");
        try (var paths = Files.walk(root)) {
            List<Path> violations = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !root.relativize(path).startsWith("dsl"))
                    .filter(path -> !root.relativize(path).startsWith("jdbc"))
                    .filter(path -> !root.relativize(path).startsWith("json"))
                    .filter(path -> !root.relativize(path).startsWith("spec"))
                    .filter(path -> !root.relativize(path).startsWith("expression"))
                    .filter(path -> references(path, implementationDependency)).toList();
            assertTrue(violations.isEmpty(), () -> "Public capabilities depend on DSL/JDBC: " + violations);
        }
    }

    private static boolean references(Path path, Pattern pattern) {
        try {
            return pattern.matcher(Files.readString(path)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }
}
