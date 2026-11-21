package com.red_escuela.backend.academicrecord.specialist;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarFeedTokenFactoryTest {

    private final CalendarFeedTokenFactory factory = new CalendarFeedTokenFactory();

    @Test
    void tokenIs256BitsBase64UrlWithoutPadding() {
        CalendarFeedTokenFactory.GeneratedCalendarFeedToken generated = factory.create();

        assertThat(generated.token()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(generated.token())).hasSize(32);
    }

    @Test
    void storedHashIsSha256HexOfTheToken() {
        CalendarFeedTokenFactory.GeneratedCalendarFeedToken generated = factory.create();

        assertThat(generated.tokenHash()).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(CalendarFeedTokenHasher.hash(generated.token()));
        assertThat(generated.tokenHash()).doesNotContain(generated.token());
        assertThat(CalendarFeedTokenHasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void tokensDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        for (int index = 0; index < 500; index++) {
            tokens.add(factory.create().token());
        }

        assertThat(tokens).hasSize(500);
    }

    @Test
    void malformedCandidatesAreRejected() {
        assertThat(CalendarFeedTokenFactory.isWellFormed(factory.create().token())).isTrue();
        assertThat(CalendarFeedTokenFactory.isWellFormed(null)).isFalse();
        assertThat(CalendarFeedTokenFactory.isWellFormed("short")).isFalse();
        assertThat(CalendarFeedTokenFactory.isWellFormed("a".repeat(44))).isFalse();
        assertThat(CalendarFeedTokenFactory.isWellFormed("a".repeat(42) + "=")).isFalse();
        assertThat(CalendarFeedTokenFactory.isWellFormed("../" + "a".repeat(40))).isFalse();
    }
}
