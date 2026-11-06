package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.exception.InvalidCertificateCodeException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateCodeTest {

    @Test
    void generatedCodeHasSixteenCrockfordCharacters() {
        CertificateCode code = CertificateCode.generate(new SecureRandom());

        assertThat(code.value()).hasSize(16).matches("[0-9A-HJKMNP-TV-Z]{16}");
        assertThat(code.formatted()).matches("[0-9A-Z]{4}(-[0-9A-Z]{4}){3}");
    }

    @Test
    void normalizationIgnoresHyphensAndCase() {
        CertificateCode parsed = CertificateCode.parse(" 7qk3-m9xh 2vbn-4t8r ");

        assertThat(parsed.value()).isEqualTo("7QK3M9XH2VBN4T8R");
    }

    @Test
    void ambiguousCharactersAreNeverGenerated() {
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 500; i++) {
            assertThat(CertificateCode.generate(random).value()).doesNotContainPattern("[ILOU]");
        }
    }

    @Test
    void invalidLengthIsRejected() {
        assertThatThrownBy(() -> CertificateCode.parse("7QK3M9XH")).isInstanceOf(InvalidCertificateCodeException.class);
        assertThatThrownBy(() -> CertificateCode.parse(null)).isInstanceOf(InvalidCertificateCodeException.class);
    }

    @Test
    void charactersOutsideTheAlphabetAreRejected() {
        assertThatThrownBy(() -> CertificateCode.parse("7QK3M9XH2VBN4T8L")).isInstanceOf(InvalidCertificateCodeException.class);
    }
}
