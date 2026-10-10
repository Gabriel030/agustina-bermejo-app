package com.red_escuela.backend.billing.decision;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-170")
class BillingDecisionRegistryTest {

    private static final String HEADER = "| ID | Área | Decisión | Estado | Configurable | Implementación | Caso de test |\n|---|---|---|---|---|---|---|\n";

    private static String row(String id, String status, String configurable, String implementation, String testCase) {
        return "| " + id + " | Mora | Texto | " + status + " | " + configurable + " | " + implementation + " | " + testCase + " |\n";
    }

    private static List<String> errorsOf(String rows) {
        return catchErrors(HEADER + rows);
    }

    private static List<String> catchErrors(String content) {
        try {
            DecisionRegistry.parse(content);
            return List.of();
        } catch (InvalidDecisionRegistryException e) {
            return e.errors();
        }
    }

    @Test
    void parsesEveryRowOfTheRepositoryRegistry() {
        DecisionRegistry registry = DecisionRegistry.readFile(Path.of("../docs/billing/decision-registry.md"));

        assertThat(registry.size()).isEqualTo(235);
        assertThat(registry.find("D-001")).isPresent();
        assertThat(registry.find("D-177")).isPresent();
        assertThat(registry.find("D-170").orElseThrow().implementation()).isEqualTo("implementada");
    }

    @Test
    void failsWithThePathWhenTheRegistryFileDoesNotExist() {
        assertThatThrownBy(() -> DecisionRegistry.readFile(Path.of("../docs/billing/missing-registry.md")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-registry.md");
    }

    @Test
    void parsesAValidRowOfEveryShape() {
        DecisionRegistry registry = DecisionRegistry.parse(HEADER
                + row("D-001", "vigente", "no", "implementada", "Dado A, cuando B, entonces C.")
                + row("D-002", "vigente", "sí (por carrera)", "parcial (V2-03)", "Dado A, cuando B, entonces C.")
                + row("D-003", "vigente", "sí", "pendiente (V2-09)", "Dado A.")
                + row("D-004", "vigente", "no", "violada (V2-03): billing/Invoice.java:10", "Dado A.")
                + row("D-005", "a confirmar", "no", "implementada", "Dado A.")
                + row("D-006", "pendiente", "no", "no aplica", "—")
                + row("D-007", "reemplazada por D-001, D-002", "no", "no aplica", "—")
                + row("D-008", "resuelta por D-003", "no", "no aplica", "—")
                + row("D-009", "vigente", "no", "no aplica", "—"));

        assertThat(registry.size()).isEqualTo(9);
        DecisionRegistryRow first = registry.find("D-001").orElseThrow();
        assertThat(first.isCurrent()).isTrue();
        assertThat(first.isImplementedOrPartial()).isTrue();
        assertThat(registry.find("D-004").orElseThrow().isViolated()).isTrue();
        assertThat(registry.find("D-005").orElseThrow().isToConfirm()).isTrue();
        assertThat(registry.find("D-007").orElseThrow().isCurrent()).isFalse();
        assertThat(registry.find("D-009").orElseThrow().isNotApplicable()).isTrue();
        assertThat(registry.find("D-003").orElseThrow().isImplementedOrPartial()).isFalse();
    }

    @Test
    void anEscapedPipeStaysInsideItsCell() {
        DecisionRegistry registry = DecisionRegistry.parse(HEADER
                + "| D-001 | Mora | Una \\| otra | vigente | no | implementada | Dado a \\| b. |\n");

        DecisionRegistryRow row = registry.find("D-001").orElseThrow();
        assertThat(row.decision()).isEqualTo("Una | otra");
        assertThat(row.testCase()).isEqualTo("Dado a | b.");
    }

    @Test
    void rejectsAnInvalidStatusNamingTheIdAndTheColumn() {
        assertThat(errorsOf(row("D-001", "casi vigente", "no", "implementada", "Dado A.")))
                .containsExactly("D-001 column Estado: invalid value 'casi vigente'");
    }

    @Test
    void rejectsAnInvalidConfigurableValue() {
        assertThat(errorsOf(row("D-001", "vigente", "tal vez", "implementada", "Dado A.")))
                .containsExactly("D-001 column Configurable: invalid value 'tal vez'");
    }

    @Test
    void rejectsAnInvalidImplementationValue() {
        assertThat(errorsOf(row("D-001", "vigente", "no", "hecha", "Dado A.")))
                .containsExactly("D-001 column Implementación: invalid value 'hecha'");
        assertThat(errorsOf(row("D-002", "vigente", "no", "violada (V2-03)", "Dado A.")))
                .containsExactly("D-002 column Implementación: invalid value 'violada (V2-03)'");
        assertThat(errorsOf(row("D-003", "vigente", "no", "parcial", "Dado A.")))
                .containsExactly("D-003 column Implementación: invalid value 'parcial'");
    }

    @Test
    void rejectsAnIdThatDoesNotMatchTheFormat() {
        assertThat(errorsOf(row("D-1234", "vigente", "no", "implementada", "Dado A.")))
                .containsExactly("D-1234 column ID: must match D-nnn");
    }

    @Test
    void rejectsADuplicatedId() {
        assertThat(errorsOf(row("D-001", "vigente", "no", "implementada", "Dado A.")
                + row("D-001", "vigente", "no", "implementada", "Dado B.")))
                .containsExactly("D-001 column ID: duplicated");
    }

    @Test
    void rejectsAReplacementToANonExistentId() {
        assertThat(errorsOf(row("D-001", "reemplazada por D-099", "no", "no aplica", "—")))
                .containsExactly("D-001 column Estado: references D-099 which does not exist");
    }

    @Test
    void requiresATestCaseForACurrentDecisionThatApplies() {
        assertThat(errorsOf(row("D-001", "vigente", "no", "implementada", "—")))
                .containsExactly("D-001 column Caso de test: required for a current decision that applies");
        assertThat(errorsOf(row("D-002", "a confirmar", "no", "pendiente (V2-01)", "")))
                .containsExactly("D-002 column Caso de test: required for a current decision that applies");
    }

    @Test
    void doesNotRequireATestCaseWhenTheDecisionDoesNotApplyOrIsNotCurrent() {
        assertThat(errorsOf(row("D-001", "vigente", "no", "no aplica", "—")
                + row("D-002", "pendiente", "no", "no aplica", "—"))).isEmpty();
    }

    @Test
    void rejectsARowWithAWrongNumberOfColumns() {
        assertThat(errorsOf("| D-001 | Mora | Texto | vigente |\n"))
                .containsExactly("D-001 expected 7 columns but found 4");
    }

    @Test
    void reportsEveryErrorOfEveryRow() {
        assertThat(errorsOf(row("D-001", "x", "y", "z", "Dado A.")))
                .hasSize(3);
    }
}
