package com.red_escuela.backend.shared.domain.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyEvaluationTest {

    @Test
    @Tag("D-096")
    void throwIfDeniedCarriesEveryViolationInOrder() {
        PolicyEvaluationResult evaluation = new PolicyEvaluationResult(List.of(
                PolicyVerdict.deny("A", "FIRST", "primera"),
                PolicyVerdict.allow("B"),
                PolicyVerdict.deny("C", "SECOND", "segunda")));

        assertThatThrownBy(() -> evaluation.throwIfDenied(violations -> new CarryingException(violations)))
                .isInstanceOfSatisfying(CarryingException.class, e -> assertThat(e.violations).containsExactly(
                        new PolicyViolation("FIRST", "primera"), new PolicyViolation("SECOND", "segunda")));
    }

    @Test
    void throwIfDeniedDoesNothingWhenEverythingIsAllowed() {
        PolicyEvaluationResult evaluation = new PolicyEvaluationResult(List.of(PolicyVerdict.allow("A")));

        assertThatCode(() -> evaluation.throwIfDenied(CarryingException::new)).doesNotThrowAnyException();
        assertThat(evaluation.violations()).isEmpty();
    }

    private static final class CarryingException extends RuntimeException {
        private final List<PolicyViolation> violations;

        private CarryingException(List<PolicyViolation> violations) {
            this.violations = violations;
        }
    }
}
