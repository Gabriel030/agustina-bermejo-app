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
                Path.of("src/main/java"), List.of("/billing/configuration/", "/billing/operation/", "/billing/discount/"));

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

    @Test
    void theMonthlyInvoicePlannersAndTheEnrollmentFeeRequirementServiceHaveNoBranching() {
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (String file : List.of("billing/planning/service/ProgramMonthlyInvoicePlanner.java", "billing/planning/service/CourseMonthlyInvoicePlanner.java",
                "billing/service/EnrollmentFeeRequirementService.java")) {
            violations.addAll(UseCaseBranchingScanner.scanSource(file, "@UseCase\n" + readMain(file)));
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void detectsAnIfInAPlannerSource() {
        assertThat(UseCaseBranchingScanner.scanSource("FixturePlanner.java", "@UseCase\nclass FixturePlanner {\n    void plan(boolean x) {\n        if (x) {\n        }\n    }\n}\n")).hasSize(1);
    }

    private static String readMain(String file) {
        try {
            return java.nio.file.Files.readString(Path.of("src/main/java/com/red_escuela/backend", file));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
