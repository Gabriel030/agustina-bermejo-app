package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void valueIsTrimmedAndLowercased() {
        assertThat(EmailAddress.of(" Ana.Perez@Mail.com ").value()).isEqualTo("ana.perez@mail.com");
    }

    @Test
    void addressesDifferingOnlyInCaseAreEqual() {
        assertThat(EmailAddress.of("ANA@mail.com")).isEqualTo(EmailAddress.of("ana@MAIL.com"));
    }

    @Test
    void textWithoutAtIsRejected() {
        assertThatThrownBy(() -> EmailAddress.of("ana.perez.mail.com")).isInstanceOf(InvalidEmailAddressException.class);
    }

    @Test
    void malformedAddressesAreRejected() {
        assertThatThrownBy(() -> EmailAddress.of("")).isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> EmailAddress.of("   ")).isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> EmailAddress.of("@mail.com")).isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> EmailAddress.of("ana@")).isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> EmailAddress.of("ana@@mail.com")).isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> EmailAddress.of("ana perez@mail.com")).isInstanceOf(InvalidEmailAddressException.class);
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> EmailAddress.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void invalidAddressMessageDoesNotLeakTheInput() {
        assertThatThrownBy(() -> EmailAddress.of("secret-name"))
                .hasMessageNotContaining("secret-name");
    }
}
