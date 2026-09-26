package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.TimeZone;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
class TimeZoneContextTest extends AbstractIntegrationTest {

    @Autowired
    private TimeZoneConfig timeZoneConfig;

    @Test
    void theLoadedContextAppliesTheConfiguredZoneWithoutGoingThroughMain() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(timeZoneConfig.getConfiguredZoneId());
    }

    @Test
    void localDateTimeNowFollowsTheConfiguredZoneEvenOnAnUtcRunner() {
        assertThat(LocalDateTime.now())
                .isCloseTo(LocalDateTime.now(timeZoneConfig.getConfiguredZoneId()), within(5, SECONDS));
    }
}
