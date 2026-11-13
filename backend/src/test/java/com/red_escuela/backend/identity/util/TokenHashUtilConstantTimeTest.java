package com.red_escuela.backend.identity.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashUtilConstantTimeTest {

    @Test
    void equalValuesMatch() {
        assertThat(TokenHashUtil.constantTimeEquals("abc123", "abc123")).isTrue();
    }

    @Test
    void differentValuesOfSameOrDifferentLengthDoNotMatch() {
        assertThat(TokenHashUtil.constantTimeEquals("abc123", "abc124")).isFalse();
        assertThat(TokenHashUtil.constantTimeEquals("abc123", "abc")).isFalse();
    }

    @Test
    void nullValuesNeverMatch() {
        assertThat(TokenHashUtil.constantTimeEquals(null, "abc")).isFalse();
        assertThat(TokenHashUtil.constantTimeEquals("abc", null)).isFalse();
        assertThat(TokenHashUtil.constantTimeEquals(null, null)).isFalse();
    }
}
