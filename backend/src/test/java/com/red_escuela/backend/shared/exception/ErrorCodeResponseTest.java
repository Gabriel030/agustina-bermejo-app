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

    @Test
    @org.junit.jupiter.api.Tag("D-096")
    void anExceptionWithoutViolationsSerializesNoViolationsField() throws Exception {
        tools.jackson.databind.JsonNode json = new tools.jackson.databind.ObjectMapper()
                .valueToTree(ErrorCodeResponse.of(new CohortAdmissionWindowClosedException(1L)));

        assertThat(json.get("code").asText()).isEqualTo("COHORT_ADMISSION_WINDOW_CLOSED");
        assertThat(json.get("retryable").asBoolean()).isFalse();
        assertThat(json.has("violations")).isFalse();
    }
}
