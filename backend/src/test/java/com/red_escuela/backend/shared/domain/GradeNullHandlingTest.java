package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GradeNullHandlingTest {

    @Test
    void ofNullableReturnsNullForNullAndAGradeOtherwise() {
        assertThat(Grade.ofNullable(null)).isNull();
        assertThat(Grade.ofNullable(new BigDecimal("7.50"))).isEqualTo(Grade.of(new BigDecimal("7.50")));
    }

    @Test
    void valueOrNullReturnsNullForNullAndTheValueOtherwise() {
        assertThat(Grade.valueOrNull(null)).isNull();
        assertThat(Grade.valueOrNull(Grade.of(new BigDecimal("7.50")))).isEqualTo(Grade.of(new BigDecimal("7.50")).value());
    }
}
