package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.graduation.certificate.verification.specialist.DocumentMasker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMaskerTest {

    @Test
    void masksAllButLastThreeCharacters() {
        assertThat(DocumentMasker.mask("12345678")).isEqualTo("*****678");
    }

    @Test
    void shortDocumentsNeverShowMoreThanHalfOfTheirCharacters() {
        assertThat(DocumentMasker.mask("123")).isEqualTo("**3");
        assertThat(DocumentMasker.mask("12")).isEqualTo("*2");
        assertThat(DocumentMasker.mask("1")).isEqualTo("*");
        assertThat(DocumentMasker.mask("123456")).isEqualTo("***456");
    }

    @Test
    void nullDocumentBecomesEmpty() {
        assertThat(DocumentMasker.mask(null)).isEmpty();
    }
}
