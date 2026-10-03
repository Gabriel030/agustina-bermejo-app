package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdSanitizationTest {

    @Test
    void generatesOneWhenTheHeaderIsAbsentOrBlank() {
        assertThat(RequestIdFilter.sanitize(null)).isNotBlank();
        assertThat(RequestIdFilter.sanitize("   ")).isNotBlank();
        assertThat(RequestIdFilter.sanitize(null)).isNotEqualTo(RequestIdFilter.sanitize(null));
    }

    @Test
    void keepsAReasonableValueAsIs() {
        assertThat(RequestIdFilter.sanitize("abc-123")).isEqualTo("abc-123");
    }

    @Test
    void stripsControlCharactersSoALogLineCannotBeForged() {
        assertThat(RequestIdFilter.sanitize("abc\n2026-01-01 ERROR falso")).doesNotContain("\n").doesNotContain(" ");
    }

    @Test
    void truncatesAnOversizedValue() {
        String enorme = "x".repeat(5000);
        assertThat(RequestIdFilter.sanitize(enorme)).hasSize(RequestIdFilter.MAX_LENGTH);
    }

    @Test
    void fallsBackWhenEverythingWasStripped() {
        assertThat(RequestIdFilter.sanitize("\n\t\r")).isNotBlank().doesNotContain("\n");
    }
}
