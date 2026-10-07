package com.red_escuela.backend.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-051")
class UseCaseBranchingTest {

    @Test
    void useCaseServicesOfTheV2SubdomainsHaveNoBranching() {
        List<String> violations = UseCaseBranchingScanner.findViolations(
                Path.of("src/main/java"), List.of("/billing/configuration/", "/billing/operation/"));

        assertThat(violations).isEmpty();
    }

    @Test
    void detectsAnIf() {
        String source = "@UseCase\npublic class FixtureService {\n    void run(boolean x) {\n        if (x) {\n        }\n    }\n}\n";

        assertThat(UseCaseBranchingScanner.scanSource("FixtureService.java", source))
                .containsExactly("FixtureService.java:4 if (x) {");
    }

    @Test
    void detectsASwitch() {
        String source = "@UseCase\npublic class FixtureService {\n    int run(int x) {\n        switch (x) {\n            default: return 1;\n        }\n    }\n}\n";

        assertThat(UseCaseBranchingScanner.scanSource("FixtureService.java", source)).hasSize(1);
    }

    @Test
    void detectsATernary() {
        String source = "@UseCase\npublic class FixtureService {\n    int run(boolean x) {\n        return x ? 1 : 2;\n    }\n}\n";

        assertThat(UseCaseBranchingScanner.scanSource("FixtureService.java", source))
                .containsExactly("FixtureService.java:4 return x ? 1 : 2;");
    }

    @Test
    void ignoresAServiceWithoutTheUseCaseAnnotationAndACleanOne() {
        String plain = "public class PlainService {\n    void run(boolean x) {\n        if (x) {\n        }\n    }\n}\n";
        String clean = "@UseCase\npublic class CleanService {\n    void run(Runnable policy) {\n        policy.run();\n    }\n}\n";

        assertThat(UseCaseBranchingScanner.scanSource("PlainService.java", plain)).isEmpty();
        assertThat(UseCaseBranchingScanner.scanSource("CleanService.java", clean)).isEmpty();
    }

    @Test
    void doesNotConfuseGenericWildcardsOrIdentifiersContainingIf() {
        String source = "@UseCase\npublic class FixtureService {\n    java.util.List<?> run() {\n        return notifyIfNeeded();\n    }\n}\n";

        assertThat(UseCaseBranchingScanner.scanSource("FixtureService.java", source)).isEmpty();
    }
}
