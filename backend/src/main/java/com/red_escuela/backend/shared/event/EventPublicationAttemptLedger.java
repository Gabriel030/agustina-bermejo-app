package com.red_escuela.backend.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

public class EventPublicationAttemptLedger {
    private static final Logger log = LoggerFactory.getLogger(EventPublicationAttemptLedger.class);

    private static final String MOVE_TO_DEAD_LETTER = """
            with exhausted as (
              select p.id, p.listener_id, p.event_type, p.serialized_event, p.publication_date, a.attempts
              from event_publication p join event_publication_attempt a on a.publication_id = p.id
              where p.completion_date is null and a.attempts >= ?),
            moved as (
              insert into event_publication_dead_letter
                (publication_id, listener_id, event_type, serialized_event, publication_date, attempts, dead_lettered_at)
              select id, listener_id, event_type, serialized_event, publication_date, attempts, now() from exhausted
              returning publication_id),
            removed as (
              delete from event_publication where id in (select publication_id from moved) returning id)
            delete from event_publication_attempt where publication_id in (select id from removed)
            """;
    private static final String COUNT_ATTEMPTS = """
            insert into event_publication_attempt (publication_id, attempts, last_attempt_at)
            select id, 1, now() from event_publication
            where completion_date is null and publication_date < now() - make_interval(secs => ?)
            on conflict (publication_id) do update
              set attempts = event_publication_attempt.attempts + 1, last_attempt_at = now()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int maxAttempts;

    public EventPublicationAttemptLedger(JdbcTemplate jdbcTemplate, int maxAttempts) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxAttempts = maxAttempts;
    }

    public void moveExhaustedPublicationsToDeadLetter() {
        List<Map<String, Object>> exhausted = jdbcTemplate.queryForList(
                "select p.id, p.listener_id, p.event_type, a.attempts from event_publication p "
                        + "join event_publication_attempt a on a.publication_id = p.id "
                        + "where p.completion_date is null and a.attempts >= ?", maxAttempts);
        if (exhausted.isEmpty()) {
            return;
        }
        jdbcTemplate.update(MOVE_TO_DEAD_LETTER, maxAttempts);
        exhausted.forEach(row -> log.error("Event publication moved to dead letter after {} attempts: id={}, listener={}, eventType={}",
                row.get("attempts"), row.get("id"), row.get("listener_id"), row.get("event_type")));
    }

    public void countAttemptsOfPendingPublications() {
        jdbcTemplate.update(COUNT_ATTEMPTS, EventPublicationResubmissionJob.MINIMUM_AGE.toSeconds());
    }

    public void forgetAttemptsOfFinishedPublications() {
        jdbcTemplate.update("delete from event_publication_attempt where publication_id not in (select id from event_publication)");
    }
}
