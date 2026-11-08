package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateAcademicOfferingCycleRequestTest {

    @Test
    void requestWithoutProgramNorCourseIsRejected() {
        assertThatThrownBy(() -> new CreateAcademicOfferingCycleRequest(null, null, 3L))
                .isInstanceOf(RequestFieldRequiredException.class);
    }

    @Test
    void requestWithProgramIsAccepted() {
        assertThatCode(() -> new CreateAcademicOfferingCycleRequest(1L, null, 3L)).doesNotThrowAnyException();
    }

    @Test
    void requestWithCourseIsAccepted() {
        assertThatCode(() -> new CreateAcademicOfferingCycleRequest(null, 2L, 3L)).doesNotThrowAnyException();
    }
}
