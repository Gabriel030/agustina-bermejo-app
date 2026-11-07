package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.exception.InvalidCertificateCodeException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateCodeTest {

    @Test
    void generatedCodeHasTwentySixCrockfordCharacters() {
        CertificateCode code = CertificateCode.generate(new SecureRandom());

        assertThat(code.value()).hasSize(26).matches("[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(code.formatted()).matches("[0-9A-Z]{5}(-[0-9A-Z]{5}){3}-[0-9A-Z]{6}");
    }

    @Test
    void codeCarriesAtLeastOneHundredTwentyEightBitsOfEntropy() {
        assertThat(CertificateCode.ENTROPY_BITS).isGreaterThanOrEqualTo(128);
        assertThat(CertificateCode.LENGTH * (Math.log(CertificateCode.ALPHABET.length()) / Math.log(2))).isGreaterThanOrEqualTo(128);
    }

    @Test
    void tenThousandGeneratedCodesAreAllDifferent() {
        SecureRandom random = new SecureRandom();
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            codes.add(CertificateCode.generate(random).value());
        }

        assertThat(codes).hasSize(10_000);
    }

    @Test
    void legacySixteenCharacterCodesAreRejected() {
        assertThatThrownBy(() -> CertificateCode.parse("7QK3M9XH2VBN4T8R")).isInstanceOf(InvalidCertificateCodeException.class);
    }

    @Test
    void normalizationIgnoresHyphensAndCase() {
        CertificateCode parsed = CertificateCode.parse(" 7qk3m-9xh2v bn4t8-r5w6y-z0a1b2 ");

        assertThat(parsed.value()).isEqualTo("7QK3M9XH2VBN4T8R5W6YZ0A1B2");
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
        assertThatThrownBy(() -> CertificateCode.parse("7QK3M9XH2VBN4T8L5W6YZ0A1B2")).isInstanceOf(InvalidCertificateCodeException.class);
    }

    @Test
    void nonAsciiCharactersAreRejectedInsteadOfBeingUppercasedIntoTheAlphabet() {
        assertThat("\u017F".toUpperCase(java.util.Locale.ROOT)).isEqualTo("S");
        assertThatThrownBy(() -> CertificateCode.parse("7QK3M9XH2VBN4T8R5W6YZ0A1B\u017F")).isInstanceOf(InvalidCertificateCodeException.class);
    }
}
