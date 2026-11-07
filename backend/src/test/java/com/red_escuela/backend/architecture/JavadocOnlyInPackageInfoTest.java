package com.red_escuela.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JavadocOnlyInPackageInfoTest {

    private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    private static final Set<String> LEGACY_JAVADOC_FILES = Set.of("OpenApiSecurityConfig.java", "SecurityConfig.java");

    private static final List<String> REQUIRED_LABELS = List.of("<b>Qué es</b>", "<b>Expone</b>", "<b>No hacer</b>", "<b>Ejemplo</b>");

    @Test
    void javadocExistsOnlyInPackageInfoFiles() throws IOException {
        List<String> offenders = javaFiles()
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .filter(path -> !LEGACY_JAVADOC_FILES.contains(path.getFileName().toString()))
                .filter(JavadocOnlyInPackageInfoTest::containsJavadoc)
                .map(Path::toString)
                .sorted()
                .collect(Collectors.toList());
        assertThat(offenders).as("javadoc is only allowed in package-info.java").isEmpty();
    }

    @Test
    void documentedPackageInfosUseTheFourLabelTemplate() throws IOException {
        List<String> offenders = javaFiles()
                .filter(path -> path.getFileName().toString().equals("package-info.java"))
                .filter(JavadocOnlyInPackageInfoTest::containsJavadoc)
                .filter(path -> !hasAllLabels(path))
                .map(Path::toString)
                .sorted()
                .collect(Collectors.toList());
        assertThat(offenders).as("a documented package-info needs the labels " + REQUIRED_LABELS).isEmpty();
    }

    @Test
    void javadocDetectionRecognizesBlocksAndIgnoresPlainStrings() {
        assertThat(containsJavadocText("package a;\n/** doc */\nclass A {}")).isTrue();
        assertThat(containsJavadocText("package a;\nclass A { String s = \"/**\"; }")).isFalse();
        assertThat(containsJavadocText("package a;\nclass A {}")).isFalse();
    }

    private static Stream<Path> javaFiles() throws IOException {
        List<Path> files = new java.util.ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files.stream();
    }

    private static boolean containsJavadoc(Path path) {
        return containsJavadocText(read(path));
    }

    private static boolean containsJavadocText(String source) {
        return source.lines().anyMatch(line -> line.stripLeading().startsWith("/**"));
    }

    private static boolean hasAllLabels(Path path) {
        String source = read(path);
        return REQUIRED_LABELS.stream().allMatch(source::contains);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
