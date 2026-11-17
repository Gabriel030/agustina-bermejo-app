package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.shared.domain.InvalidEmailAddressException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelAddressTest {

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> ChannelAddress.ofEmail("not-an-email")).isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> ChannelAddress.ofEmail("a b@c.com")).isInstanceOf(InvalidEmailAddressException.class);
    }

    @Test
    void normalizesEmail() {
        assertThat(ChannelAddress.ofEmail("  Ana@Example.COM ").value()).isEqualTo("ana@example.com");
    }

    @Test
    void acceptsOnlyE164Phones() {
        assertThat(ChannelAddress.ofPhone("+5491112345678").value()).isEqualTo("+5491112345678");
        assertThatThrownBy(() -> ChannelAddress.ofPhone("1112345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChannelAddress.ofPhone("+54 9 11 1234")).isInstanceOf(IllegalArgumentException.class);
    }
}
