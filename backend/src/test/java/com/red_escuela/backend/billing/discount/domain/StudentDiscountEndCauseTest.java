package com.red_escuela.backend.billing.discount.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-284")
class StudentDiscountEndCauseTest {

    @Test
    void aRevokedDiscountIsAlwaysShownAsAnInstitutionDecision() {
        assertThat(StudentDiscountEndCause.of(StudentDiscountStatus.REVOKED)).contains(StudentDiscountEndCause.INSTITUTION_DECISION);
        assertThat(StudentDiscountEndCause.INSTITUTION_DECISION.studentFacingText()).isEqualTo("Decisión de la institución");
    }

    @Test
    void anEndedDiscountIsShownAsTheEndOfTheEnrollment() {
        assertThat(StudentDiscountEndCause.of(StudentDiscountStatus.ENDED)).contains(StudentDiscountEndCause.ENROLLMENT_ENDED);
        assertThat(StudentDiscountEndCause.ENROLLMENT_ENDED.studentFacingText()).isEqualTo("Finalizó la inscripción");
    }

    @Test
    void anActiveOrExhaustedDiscountHasNoEndCause() {
        assertThat(StudentDiscountEndCause.of(StudentDiscountStatus.ACTIVE)).isEmpty();
        assertThat(StudentDiscountEndCause.of(StudentDiscountStatus.EXHAUSTED)).isEmpty();
    }
}
