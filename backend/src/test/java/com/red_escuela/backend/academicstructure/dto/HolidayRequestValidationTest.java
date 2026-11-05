package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayRequestValidationTest {

    private static final LocalDate DATE = LocalDate.of(2030, 5, 1);

    @Test
    void createRequestWithoutNameIsRejected() {
        assertThatThrownBy(() -> new CreateHolidayRequest(DATE, " ", HolidayType.NATIONAL, null))
                .isInstanceOf(RequestFieldRequiredException.class);
    }

    @Test
    void createRequestWithoutTypeIsRejected() {
        assertThatThrownBy(() -> new CreateHolidayRequest(DATE, "X", null, null))
                .isInstanceOf(RequestFieldRequiredException.class);
    }

    @Test
    void updateRequestWithoutNameOrTypeIsRejected() {
        assertThatThrownBy(() -> new UpdateHolidayRequest(DATE, null, HolidayType.OTHER, null))
                .isInstanceOf(RequestFieldRequiredException.class);
        assertThatThrownBy(() -> new UpdateHolidayRequest(DATE, "X", null, null))
                .isInstanceOf(RequestFieldRequiredException.class);
    }

    @Test
    void requestsWithoutDescriptionAreAccepted() {
        assertThatCode(() -> new CreateHolidayRequest(DATE, "X", HolidayType.BRIDGE, null)).doesNotThrowAnyException();
        assertThatCode(() -> new UpdateHolidayRequest(DATE, "X", HolidayType.BRIDGE, null)).doesNotThrowAnyException();
    }
}
