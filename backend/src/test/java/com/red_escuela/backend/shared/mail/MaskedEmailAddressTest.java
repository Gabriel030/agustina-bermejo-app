package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.domain.EmailAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MaskedEmailAddressTest {

    @ParameterizedTest
    @CsvSource({
            "maria.lopez@example.com,ma***@example.com",
            "abc@x.org,ab***@x.org",
            "ab@x.org,a***@x.org",
            "a@x.org,***@x.org"})
    void theMaskKeepsAtMostTwoCharactersAndNeverTheWholeLocalPart(String address, String expected) {
        assertThat(MaskedEmailAddress.of(EmailAddress.of(address))).isEqualTo(expected);
    }

    @Test
    void theMaskNeverRevealsTheLocalPartOfALongAddress() {
        String masked = MaskedEmailAddress.of(EmailAddress.of("maria.lopez@example.com"));

        assertThat(masked).doesNotContain("lopez").doesNotContain("maria");
    }
}
