package com.red_escuela.backend.shared.event;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventPublicationCircuitDeferralIT extends AbstractIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final String MAIL_LISTENER = EventPublicationResubmissionJob.MAIL_LISTENER_PREFIX + "onPasswordResetCodeIssued(Event)";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicLong rejections = new AtomicLong();

    private EventPublicationAttemptLedger newLedger() {
        return new EventPublicationAttemptLedger(jdbcTemplate, MAX_ATTEMPTS, rejections::get, EventPublicationResubmissionJob.MAIL_LISTENER_PREFIX);
    }

    private UUID insertPending(String listener) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date) "
                + "values (?, ?, 'Probe', '{}', now() - interval '10 minutes')", id, listener);
        return id;
    }

    private int attemptsOf(UUID id) {
        return jdbcTemplate.queryForObject("select coalesce(max(attempts), 0) from event_publication_attempt where publication_id = ?", Integer.class, id);
    }

    @Test
    void mailPublicationsRejectedByAnOpenCircuitNeverReachTheDeadLetterNoMatterHowLongItStaysOpen() {
        UUID mail = insertPending(MAIL_LISTENER);
        UUID other = insertPending("other-listener");
        EventPublicationAttemptLedger ledger = newLedger();

        for (int run = 1; run <= MAX_ATTEMPTS * 3; run++) {
            rejections.addAndGet(5);
            ledger.moveExhaustedPublicationsToDeadLetter();
            ledger.countAttemptsOfPendingPublications();
        }

        assertThat(attemptsOf(mail)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from event_publication where id = ?", Integer.class, mail)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from event_publication_dead_letter where publication_id = ?", Integer.class, mail)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from event_publication_dead_letter where publication_id = ?", Integer.class, other)).isEqualTo(1);
    }

    @Test
    void mailPublicationsThatFailForOtherReasonsStillCountAndEndInDeadLetter() {
        UUID mail = insertPending(MAIL_LISTENER);
        EventPublicationAttemptLedger ledger = newLedger();

        for (int run = 1; run <= MAX_ATTEMPTS; run++) {
            ledger.countAttemptsOfPendingPublications();
            assertThat(attemptsOf(mail)).isEqualTo(run);
        }
        ledger.moveExhaustedPublicationsToDeadLetter();

        assertThat(jdbcTemplate.queryForObject("select count(*) from event_publication_dead_letter where publication_id = ?", Integer.class, mail)).isEqualTo(1);
    }
}
