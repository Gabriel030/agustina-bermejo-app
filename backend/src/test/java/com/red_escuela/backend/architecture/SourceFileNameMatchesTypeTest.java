package com.red_escuela.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFileNameMatchesTypeTest {

    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "^(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static|strictfp)\\s+)*"
                    + "(?:@interface|class|interface|enum|record)\\s+(\\w+)",
            Pattern.MULTILINE);

    @Test
    void everyJavaSourceDeclaresATopLevelTypeNamedLikeItsFile() {
        List<String> mismatches = mismatchesIn(Path.of("src/main/java"), Path.of("src/test/java"));

        assertThat(mismatches)
                .as("archivos .java cuyo nombre no coincide con ninguna clase top-level (rompe la compilacion incremental de maven)")
                .isEmpty();
    }

    @Test
    void detectsAFileWhoseTypeHasADifferentName(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("WrongFileName.java"), "package a;\n\nclass RightTypeName {\n}\n");
        Files.writeString(root.resolve("Matching.java"), "package a;\n\npublic final class Matching {\n}\n");
        Files.writeString(root.resolve("WithHelper.java"), "package a;\n\npublic record WithHelper() {\n}\n\nclass Helper {\n}\n");
        Files.writeString(root.resolve("package-info.java"), "package a;\n");

        assertThat(mismatchesIn(root)).hasSize(1).first().asString().contains("WrongFileName.java");
    }

    private static List<String> mismatchesIn(Path... roots) {
        return Arrays.stream(roots)
                .flatMap(SourceFileNameMatchesTypeTest::javaFiles)
                .filter(path -> !declaresTypeNamedLikeFile(path))
                .map(Path::toString)
                .sorted()
                .toList();
    }

    private static Stream<Path> javaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> !path.getFileName().toString().equals("module-info.java"))
                    .toList()
                    .stream();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean declaresTypeNamedLikeFile(Path path) {
        try {
            String expected = path.getFileName().toString().replace(".java", "");
            Matcher matcher = TOP_LEVEL_TYPE.matcher(Files.readString(path));
            while (matcher.find()) {
                if (matcher.group(1).equals(expected)) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
