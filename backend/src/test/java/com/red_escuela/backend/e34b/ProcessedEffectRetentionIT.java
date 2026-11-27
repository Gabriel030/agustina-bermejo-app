package com.red_escuela.backend.e34b;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.event.service.ProcessedEffectRetentionJob;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProcessedEffectRetentionIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 15, 3, 45);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProcessedEffectRetentionJob job;

    private final UUID oldEvent = UUID.randomUUID();
    private final UUID recentEvent = UUID.randomUUID();

    @BeforeEach
    void insertOldAndRecentEffects() {
        MutableClock.INSTANCE.setDateTime(NOW);
        jdbc.update("insert into processed_effect (event_id, handler_name, processed_at) values (?, 'h', ?::timestamptz)",
                oldEvent, "2026-11-01T00:00:00Z");
        jdbc.update("insert into processed_effect (event_id, handler_name, processed_at) values (?, 'h', ?::timestamptz)",
                recentEvent, "2027-05-01T00:00:00Z");
    }

    @AfterEach
    void removeInsertedEffects() {
        jdbc.update("delete from processed_effect where event_id in (?, ?)", oldEvent, recentEvent);
    }

    @Test
    void effectsOlderThanSixMonthsArePurgedAndRecentOnesStay() {
        job.deleteExpiredProcessedEffects();

        assertThat(count(oldEvent)).isZero();
        assertThat(count(recentEvent)).isEqualTo(1);
    }

    private Integer count(UUID eventId) {
        return jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Integer.class, eventId);
    }
}
