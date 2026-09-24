package com.wind.integration.metrics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公共能力不依赖 DSL/JDBC 实现；精确身份合同复用已有 MetricReferenceDsl 值对象。
 *
 * @author wuxp
 * @since 2026-09-15
 */
class MetricArchitectureDependencyTests {

    /**
     * 场景：公共能力源码保持与 DSL/JDBC 实现的依赖隔离。
     * 输入：当前 metrics 生产源码，排除 dsl、jdbc、json、spec、expression 包。
     * 流程：扫描剩余 Java 文件中的 DSL/JDBC 全限定引用，仅允许共用的精确身份值对象。
     * 预期：违规列表为空；此源码规则不证明运行时装配，也不检查已排除的 json 包。
     */
    @Test
    void testPublicCapabilitiesDoNotDependOnDslOrJdbc() throws IOException {
        Path root = Path.of("src/main/java/com/wind/integration/metrics");
        Pattern implementationDependency = Pattern.compile("\\bcom\\.wind\\.integration\\.metrics\\.(?:dsl\\.(?!definition\\.MetricReferenceDsl\\b)|jdbc\\.)");
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
