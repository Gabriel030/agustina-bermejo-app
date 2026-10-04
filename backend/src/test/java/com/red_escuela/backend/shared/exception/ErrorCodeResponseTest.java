package com.red_escuela.backend.shared.exception;

import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeResponseTest {

    @Test
    void codeIsTheExceptionNameInUpperSnakeCaseWithoutSuffix() {
        assertThat(ErrorCodeResponse.of(new CohortAdmissionWindowClosedException(1L)).code())
                .isEqualTo("COHORT_ADMISSION_WINDOW_CLOSED");
    }

    @Test
    void codeOfAJdkExceptionIsAlsoUpperSnakeCase() {
        assertThat(ErrorCodeResponse.of(new IllegalArgumentException()).code()).isEqualTo("ILLEGAL_ARGUMENT");
    }
}
