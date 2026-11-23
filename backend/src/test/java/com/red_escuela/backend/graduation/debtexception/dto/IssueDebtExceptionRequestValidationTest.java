package com.red_escuela.backend.graduation.debtexception.dto;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssueDebtExceptionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean accepts(String reason) {
        return validator.validate(new IssueDebtExceptionRequest(1L, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null, null, reason, 7)).isEmpty();
    }

    @Test
    void realReasonOfTwentyCharactersIsAccepted() {
        assertThat(accepts("Cuota abonada en efectivo")).isTrue();
    }

    @Test
    void reasonPaddedWithSpacesToTwentyCharactersIsRejected() {
        assertThat(accepts("   corto motivo      ")).isFalse();
    }

    @Test
    void reasonOfOnlySpacesIsRejected() {
        assertThat(accepts(" ".repeat(25))).isFalse();
    }

    @Test
    void reasonOfNoBreakSpacesIsRejected() {
        assertThat(accepts("\u00A0".repeat(25))).isFalse();
    }

    @Test
    void reasonOfIdeographicSpacesIsRejected() {
        assertThat(accepts("\u3000".repeat(25))).isFalse();
    }

    @Test
    void nonPositiveStudentIdIsRejected() {
        assertThat(validator.validate(new IssueDebtExceptionRequest(0L, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null, null, "Cuota abonada en efectivo", 7))).isNotEmpty();
    }

    @Test
    void reasonWithLineBreakIsRejected() {
        assertThat(accepts("Cuota abonada\nen efectivo hoy")).isFalse();
    }

    @Test
    void reasonWithBidiControlIsRejected() {
        assertThat(accepts("Cuota abonada ‮oy en efectivo")).isFalse();
    }
}
