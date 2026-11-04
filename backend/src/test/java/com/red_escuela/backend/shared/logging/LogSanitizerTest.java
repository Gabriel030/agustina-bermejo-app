package com.red_escuela.backend.shared.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void lineBreaksAreReplacedSoAForgedLogLineCannotBeInjected() {
        assertThat(LogSanitizer.sanitize("123\r\nWARN forged entry\n")).isEqualTo("123__WARN forged entry_");
    }

    @Test
    void unicodeLineAndParagraphSeparatorsAreReplaced() {
        assertThat(LogSanitizer.sanitize("a b c\u0085d")).isEqualTo("a_b_c_d");
    }

    @Test
    void aLongValueIsTruncatedWithAMark() {
        String sanitized = LogSanitizer.sanitize("x".repeat(LogSanitizer.MAX_LENGTH + 50));

        assertThat(sanitized).isEqualTo("x".repeat(LogSanitizer.MAX_LENGTH) + "...");
    }

    @Test
    void aValueAtTheLimitIsKeptWhole() {
        String atLimit = "y".repeat(LogSanitizer.MAX_LENGTH);

        assertThat(LogSanitizer.sanitize(atLimit)).isEqualTo(atLimit);
    }

    @Test
    void nullIsRenderedAsTheWordNull() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
    }

    @Test
    void ordinaryTextWithAccentsIsLeftUntouched() {
        assertThat(LogSanitizer.sanitize("Inscripción 42 - ñandú")).isEqualTo("Inscripción 42 - ñandú");
    }
}
