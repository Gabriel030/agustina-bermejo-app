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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventPublicationDeadLetterIT extends AbstractIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private EventPublicationAttemptLedger newLedger() {
        return new EventPublicationAttemptLedger(jdbcTemplate, MAX_ATTEMPTS);
    }

    private void runLedgerCycle(EventPublicationAttemptLedger ledger) {
        ledger.moveExhaustedPublicationsToDeadLetter();
        ledger.countAttemptsOfPendingPublications();
        ledger.forgetAttemptsOfFinishedPublications();
    }

    private UUID insertPublication(String listener, String minutesOld, boolean completed) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date) "
                + "values (?, ?, 'Probe', '{}', now() - make_interval(mins => ?::int), " + (completed ? "now()" : "null") + ")",
                id, listener, Integer.parseInt(minutesOld));
        return id;
    }

    private int attemptsOf(UUID id) {
        return jdbcTemplate.queryForObject("select coalesce(max(attempts), 0) from event_publication_attempt where publication_id = ?", Integer.class, id);
    }

    private int count(String table, UUID id, String column) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, id);
    }

    @Test
    void aPublicationThatKeepsFailingIsMovedToDeadLetterAfterTheMaximumAttemptsAndStopsBeingRetried() {
        UUID poisoned = insertPublication("poisoned-listener", "10", false);
        EventPublicationAttemptLedger ledger = newLedger();

        for (int run = 1; run <= MAX_ATTEMPTS; run++) {
            runLedgerCycle(ledger);
            assertThat(attemptsOf(poisoned)).isEqualTo(run);
            assertThat(count("event_publication", poisoned, "id")).isEqualTo(1);
        }
        runLedgerCycle(ledger);

        assertThat(count("event_publication", poisoned, "id")).isZero();
        assertThat(count("event_publication_attempt", poisoned, "publication_id")).isZero();
        assertThat(jdbcTemplate.queryForMap("select listener_id, attempts from event_publication_dead_letter where publication_id = ?", poisoned))
                .containsEntry("listener_id", "poisoned-listener").containsEntry("attempts", MAX_ATTEMPTS);
    }

    @Test
    void aPublicationYoungerThanTheMinimumAgeIsNotCountedAndACompletedOneIsForgotten() {
        UUID young = insertPublication("young-listener", "1", false);
        UUID completed = insertPublication("done-listener", "10", true);
        jdbcTemplate.update("insert into event_publication_attempt values (?, 2, now())", completed);

        runLedgerCycle(newLedger());

        assertThat(attemptsOf(young)).isZero();
        assertThat(count("event_publication_attempt", completed, "publication_id")).isEqualTo(1);
        jdbcTemplate.update("delete from event_publication where id = ?", completed);
        runLedgerCycle(newLedger());
        assertThat(count("event_publication_attempt", completed, "publication_id")).isZero();
        assertThat(count("event_publication_dead_letter", young, "publication_id")).isZero();
    }
}
