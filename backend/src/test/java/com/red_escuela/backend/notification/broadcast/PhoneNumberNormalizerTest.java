package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.PhoneNumberNormalizer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberNormalizerTest {

    @Test
    void normalizesArgentineMobileWithLeadingZeroAndFifteen() {
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("011 15 1234-5678")).contains("+5491112345678");
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("0351 15 612 3456")).contains("+5493516123456");
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("+54 11 15 1234 5678")).contains("+5491112345678");
    }

    @Test
    void rejectsTooShortNumber() {
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("12345")).isEmpty();
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("11 1234")).isEmpty();
    }

    @Test
    void keepsAlreadyNormalizedNumber() {
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("+5491112345678")).contains("+5491112345678");
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("+54 9 11 1234 5678")).contains("+5491112345678");
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("+54 11 1234 5678")).contains("+5491112345678");
    }

    @Test
    void stripsSpacesAndDashes() {
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("11 1234-5678")).contains("+5491112345678");
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("(11) 1234-5678")).contains("+5491112345678");
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile(" 11.1234.5678 ")).contains("+5491112345678");
    }

    @Test
    void rejectsNonArgentineOrMalformedNumbers() {
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile(null)).isEqualTo(Optional.empty());
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("")).isEmpty();
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("abc 11 1234 5678")).isEmpty();
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("+1 202 555 0123")).isEmpty();
        assertThat(PhoneNumberNormalizer.normalizeArgentineMobile("0 15 1234 5678")).isEmpty();
    }
}
