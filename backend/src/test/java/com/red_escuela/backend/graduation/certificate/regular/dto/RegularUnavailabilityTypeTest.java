package com.red_escuela.backend.graduation.certificate.regular.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RegularUnavailabilityTypeTest {

    @Test
    void exposesOnlyTheReasonsTheBackendEmits() {
        assertThat(Arrays.stream(RegularUnavailabilityType.values()).map(Enum::name))
                .containsExactlyInAnyOrder("ATTENDANCE", "PAYMENTS", "ENROLLMENT");
    }
}
