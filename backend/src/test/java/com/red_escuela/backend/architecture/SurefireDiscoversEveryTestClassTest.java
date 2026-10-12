package com.red_escuela.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SurefireDiscoversEveryTestClassTest {

    private static final Path POM = Path.of("pom.xml");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Pattern TEST_ANNOTATION =
            Pattern.compile("@(Test|ParameterizedTest|RepeatedTest|TestFactory|Property|Example)\\b");
    private static final Pattern SUREFIRE_INCLUDE = Pattern.compile("<include>\\*\\*/([^<]+)\\.java</include>");

    @Test
    void everyClassWithTestMethodsMatchesASurefireInclude() throws IOException {
        List<Pattern> includes = surefireIncludes();
        assertThat(includes).as("includes declarados en maven-surefire-plugin").isNotEmpty();

        List<String> undiscovered;
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            undiscovered = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(SurefireDiscoversEveryTestClassTest::declaresTestMethods)
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .filter(className -> includes.stream().noneMatch(include -> include.matcher(className).matches()))
                    .sorted()
                    .toList();
        }

        assertThat(undiscovered)
                .as("clases con tests que surefire no ejecutaria en verify")
                .isEmpty();
    }

    @Test
    void integrationAndPropertyClassesAreIncluded() throws IOException {
        List<Pattern> includes = surefireIncludes();

        assertThat(includes.stream().anyMatch(include -> include.matcher("SomethingIT").matches())).isTrue();
        assertThat(includes.stream().anyMatch(include -> include.matcher("SomethingProperties").matches())).isTrue();
    }

    private static List<Pattern> surefireIncludes() throws IOException {
        String pom = Files.readString(POM);
        String surefire = pom.substring(pom.indexOf("<artifactId>maven-surefire-plugin</artifactId>"));
        surefire = surefire.substring(0, surefire.indexOf("</plugin>"));
        Matcher matcher = SUREFIRE_INCLUDE.matcher(surefire);
        List<Pattern> includes = new ArrayList<>();
        while (matcher.find()) {
            includes.add(Pattern.compile(Pattern.quote(matcher.group(1)).replace("*", "\\E.*\\Q")));
        }
        return includes;
    }

    private static boolean declaresTestMethods(Path path) {
        try {
            return TEST_ANNOTATION.matcher(Files.readString(path)).find();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
