package com.red_escuela.backend.billing.decision;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-170")
class BillingDecisionCoverageRulesTest {

    private static final String HEADER = "| ID | Área | Decisión | Estado | Configurable | Implementación | Caso de test |\n|---|---|---|---|---|---|---|\n";

    private static DecisionRegistry registry(String... rows) {
        return DecisionRegistry.parse(HEADER + String.join("\n", rows) + "\n");
    }

    private static String row(String id, String status, String implementation) {
        return "| " + id + " | Mora | Texto | " + status + " | no | " + implementation + " | Dado A, entonces B. |";
    }

    private static List<TaggedTestUse> scan(String source) {
        return TaggedTestScanner.scanSources(Map.of("FixtureTest.java", source));
    }

    @Test
    void r1FailsWhenAnImplementedDecisionHasNoTag() {
        List<String> violations = DecisionCoverageRules.findViolations(
                registry(row("D-059", "vigente", "implementada")), List.of());

        assertThat(violations).containsExactly("R1 D-059 (Mora) sin test: Dado A, entonces B.");
    }

    @Test
    void r1FailsWhenAPartialDecisionHasNoTag() {
        List<String> violations = DecisionCoverageRules.findViolations(
                registry(row("D-059", "vigente", "parcial (V2-03)")), List.of());

        assertThat(violations).hasSize(1).first().asString().startsWith("R1 D-059");
    }

    @Test
    void r1DoesNotFailForDecisionsOfFuturePhasesOrViolatedOnes() {
        List<String> violations = DecisionCoverageRules.findViolations(
                registry(row("D-061", "vigente", "pendiente (V2-09)"),
                        row("D-062", "vigente", "violada (V2-03): billing/A.java:1"),
                        row("D-063", "reemplazada por D-061", "no aplica")),
                List.of());

        assertThat(violations).isEmpty();
    }

    @Test
    void r1DoesNotFailWhenTheDecisionHasATaggedMethod() {
        List<TaggedTestUse> uses = scan("""
                class FixtureTest {
                    @Test
                    @Tag("D-059")
                    void case1() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-059", "vigente", "implementada")), uses)).isEmpty();
    }

    @Test
    void aTagOnTheClassCountsForTheDecision() {
        List<TaggedTestUse> uses = scan("""
                @Tag("D-059")
                class FixtureTest {
                    @Test
                    void case1() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-059", "vigente", "implementada")), uses)).isEmpty();
    }

    @Test
    void r2FailsWhenATagCitesASupersededDecision() {
        DecisionRegistry registry = registry(row("D-058", "reemplazada por D-147", "no aplica"), row("D-147", "vigente", "pendiente (V2-03)"));
        List<TaggedTestUse> uses = scan("""
                class FixtureTest {
                    @Tag("D-058")
                    @Test
                    void case1() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry, uses))
                .containsExactly("R2 D-058 in FixtureTest.java cites a missing or superseded decision");
    }

    @Test
    void r2FailsWhenATagCitesAnUnknownDecision() {
        List<TaggedTestUse> uses = scan("""
                @Tag("D-999")
                class FixtureTest {
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-059", "vigente", "no aplica")), uses))
                .containsExactly("R2 D-999 in FixtureTest.java cites a missing or superseded decision");
    }

    @Test
    void r3FailsWhenContradictsTargetsAnImplementedDecision() {
        List<TaggedTestUse> uses = scan("""
                class FixtureTest {
                    @Tag("contradicts-D-059")
                    @Test
                    void case1() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-059", "vigente", "implementada")), uses))
                .contains("R3 contradicts-D-059 in FixtureTest.java but D-059 is not marked violada");
    }

    @Test
    void r3AllowsContradictsOnAViolatedDecision() {
        List<TaggedTestUse> uses = scan("""
                class FixtureTest {
                    @Tag("contradicts-D-142")
                    @Test
                    void case1() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-142", "vigente", "violada (V2-03): billing/A.java:1")), uses))
                .isEmpty();
    }

    @Test
    void r3FailsWhenContradictsTargetsAnUnknownDecision() {
        List<TaggedTestUse> uses = scan("@Tag(\"contradicts-D-500\")\nclass FixtureTest {\n}\n");

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-059", "vigente", "no aplica")), uses)).hasSize(1);
    }

    @Test
    void r4FailsWhenATagOnADecisionToConfirmIsInAnActiveTest() {
        List<TaggedTestUse> uses = scan("""
                class FixtureTest {
                    @Test
                    @Tag("D-161")
                    void case1() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-161", "a confirmar", "implementada")), uses))
                .containsExactly("R4 D-161 in FixtureTest.java is active but the decision is pending confirmation");
    }

    @Test
    void r4AllowsADisabledMethodOrClassOnADecisionToConfirm() {
        DecisionRegistry registry = registry(row("D-161", "a confirmar", "implementada"));
        List<TaggedTestUse> disabledMethod = scan("""
                class FixtureTest {
                    @Test
                    @Disabled("D-161 a confirmar")
                    @Tag("D-161")
                    void case1() {
                    }
                }
                """);
        List<TaggedTestUse> disabledClass = scan("""
                @Disabled("D-161 a confirmar")
                @Tag("D-161")
                class FixtureTest {
                    @Test
                    void case1() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry, disabledMethod)).isEmpty();
        assertThat(DecisionCoverageRules.findViolations(registry, disabledClass)).isEmpty();
    }

    @Test
    void aDisabledTestDoesNotHideAnActiveOneOnTheSameDecision() {
        List<TaggedTestUse> uses = scan("""
                class FixtureTest {
                    @Test
                    @Disabled
                    @Tag("D-161")
                    void disabledCase() {
                    }

                    @Test
                    @Tag("D-161")
                    void activeCase() {
                    }
                }
                """);

        assertThat(DecisionCoverageRules.findViolations(registry(row("D-161", "a confirmar", "implementada")), uses))
                .hasSize(1);
    }

    private static final String ONLY_NIGHTLY_METHOD = """
            class FixtureTest {
                @Test
                @NightlyTest(reason = "volume")
                @Tag("D-059")
                void volumeCase() {
                }
            }
            """;

    private static final String PR_METHOD = """
            class FixtureTest {
                @Test
                @Tag("D-059")
                void prCase() {
                }
            }
            """;

    private static List<String> violationsOf(String... sources) {
        List<TaggedTestUse> uses = new ArrayList<>();
        for (int i = 0; i < sources.length; i++) {
            uses.addAll(TaggedTestScanner.scanSources(Map.of("Fixture" + i + "Test.java", sources[i])));
        }
        return DecisionCoverageRules.findViolations(registry(row("D-059", "vigente", "implementada")), uses);
    }

    @Test
    void r5FailsWhenTheOnlyTaggedTestIsNightly() {
        assertThat(violationsOf(ONLY_NIGHTLY_METHOD)).singleElement().asString().startsWith("R5 D-059");
    }

    @Test
    void r5FailsWhenTheWholeClassIsNightly() {
        String source = """
                @Tag("D-059")
                @NightlyTest(reason = "volume")
                class FixtureTest {
                    @Test
                    void case1() {
                    }
                }
                """;

        assertThat(violationsOf(source)).singleElement().asString().startsWith("R5 D-059");
    }

    @Test
    void r5FailsWhenTheOnlyTaggedTestIsAMigrationTest() {
        String source = """
                @Tag("migration")
                class FixtureTest {
                    @Test
                    @Tag("D-059")
                    void case1() {
                    }
                }
                """;

        assertThat(violationsOf(source)).singleElement().asString().startsWith("R5 D-059");
    }

    @Test
    void r5FailsWhenTheOnlyTaggedTestHasTheRawNightlyTag() {
        String source = """
                class FixtureTest {
                    @Test
                    @Tag("nightly")
                    @Tag("D-059")
                    void case1() {
                    }
                }
                """;

        assertThat(violationsOf(source)).singleElement().asString().startsWith("R5 D-059");
    }

    @Test
    void r5FailsWhenTheOnlyTaggedTestIsDisabled() {
        String source = """
                class FixtureTest {
                    @Test
                    @Disabled("later")
                    @Tag("D-059")
                    void case1() {
                    }
                }
                """;

        assertThat(violationsOf(source)).singleElement().asString().startsWith("R5 D-059");
    }

    @Test
    void r5IsSatisfiedByOnePrTestEvenIfOtherTestsOfTheDecisionAreNightly() {
        assertThat(violationsOf(ONLY_NIGHTLY_METHOD, PR_METHOD)).isEmpty();
    }

    @Test
    void removingTheOnlyPrTestOfADecisionMakesTheCoverageFail() {
        assertThat(violationsOf(ONLY_NIGHTLY_METHOD, PR_METHOD)).isEmpty();
        assertThat(violationsOf(ONLY_NIGHTLY_METHOD)).hasSize(1);
    }

    @Test
    void aNightlyMarkerOnAnotherMethodDoesNotMakeTheNextTestNightly() {
        String source = """
                class FixtureTest {
                    @Test
                    @NightlyTest(reason = "volume")
                    void volumeCase() {
                    }

                    @Test
                    @Tag("D-059")
                    void prCase() {
                    }
                }
                """;

        assertThat(violationsOf(source)).isEmpty();
    }

    @Test
    void aNightlyReasonWithSemicolonsAndBracesStillMarksTheTest() {
        String source = """
                class FixtureTest {
                    @Test
                    @NightlyTest(reason = "volume; heavy {400 rows}")
                    @Tag("D-059")
                    void volumeCase() {
                    }
                }
                """;

        assertThat(violationsOf(source)).singleElement().asString().startsWith("R5 D-059");
    }

    @Test
    void anUntaggedDecisionStillReportsR1AndNotR5() {
        assertThat(violationsOf()).singleElement().asString().startsWith("R1 D-059");
    }

    @Test
    void scannerReadsEveryTagOfAMethodWithSeveralDecisions() {
        List<TaggedTestUse> uses = scan("""
                class FixtureTest {
                    @Test
                    @Tag("D-001")
                    @Tag("D-002")
                    void case1() {
                    }
                }
                """);

        assertThat(uses).extracting(TaggedTestUse::tag).containsExactly("D-001", "D-002");
        assertThat(uses).allMatch(use -> !use.disabled());
    }
}
