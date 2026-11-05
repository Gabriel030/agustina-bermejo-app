package com.red_escuela.backend.shared.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @Test
    void percentAndUnderscoreAreEscaped() {
        assertThat(LikePatterns.escape("50%_off")).isEqualTo("50\\%\\_off");
    }

    @Test
    void backslashIsEscapedFirst() {
        assertThat(LikePatterns.escape("a\\%")).isEqualTo("a\\\\\\%");
    }

    @Test
    void containsWrapsEscapedValue() {
        assertThat(LikePatterns.contains("a_b")).isEqualTo("%a\\_b%");
    }
}
