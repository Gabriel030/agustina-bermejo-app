package com.red_escuela.backend.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.TimeZone;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TimeZoneConfigTest {

    private static final String ARGENTINA = ApplicationTimeZone.DEFAULT_ZONE_ID;

    private TimeZone originalDefault;

    @BeforeEach
    void rememberDefault() {
        originalDefault = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefault() {
        TimeZone.setDefault(originalDefault);
    }

    @Test
    void applicationTimeZoneOverridesAnUtcHostTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ApplicationTimeZone.applyAsJvmDefault();

        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of(ARGENTINA));
    }

    @Test
    void theBeanAlsoAppliesTheZoneSoTestsDoNotDependOnMain() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        new TimeZoneConfig(ARGENTINA).applyAsJvmDefault();

        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of(ARGENTINA));
    }

    @Test
    void applyingTheZoneMakesLocalDateTimeNowUseIt() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        new TimeZoneConfig(ARGENTINA).applyAsJvmDefault();

        assertThat(LocalDateTime.now())
                .isCloseTo(LocalDateTime.now(ZoneId.of(ARGENTINA)), within(5, SECONDS));
    }

    @Test
    void applyingTheZoneMovesTheClockAwayFromUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LocalDateTime utcNow = LocalDateTime.now(ZoneId.of("UTC"));

        new TimeZoneConfig(ARGENTINA).applyAsJvmDefault();

        assertThat(SECONDS.between(LocalDateTime.now(), utcNow)).isGreaterThan(3 * 3600 - 60);
    }

    @Test
    void applyingTheZoneTwiceIsIdempotent() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        TimeZoneConfig config = new TimeZoneConfig(ARGENTINA);

        config.applyAsJvmDefault();
        config.applyAsJvmDefault();

        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of(ARGENTINA));
    }

    @Test
    void constructorRejectsAnUnknownZone() {
        assertThatThrownBy(() -> new TimeZoneConfig("Mars/Olympus_Mons"))
                .isInstanceOf(ZoneRulesException.class);
    }
}
