package com.red_escuela.backend.shared.event;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.event-publication.resubmission.enabled", havingValue = "true", matchIfMissing = true)
public class EventPublicationResubmissionJob {
    private static final Logger log = LoggerFactory.getLogger(EventPublicationResubmissionJob.class);
    static final Duration MINIMUM_AGE = Duration.ofMinutes(5);
    static final Duration STUCK_AFTER = Duration.ofHours(1);

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

    private final IncompleteEventPublications incompleteEventPublications;
    private final JdbcTemplate jdbcTemplate;

    private final int maxAttempts;

    public EventPublicationResubmissionJob(
            IncompleteEventPublications incompleteEventPublications,
            JdbcTemplate jdbcTemplate,
            @Value("${app.event-publication.resubmission.max-attempts:100}") int maxAttempts) {
        this.incompleteEventPublications = incompleteEventPublications;
        this.jdbcTemplate = jdbcTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M", zone = "${app.timezone}")
    @SchedulerLock(name = "EventPublicationResubmissionJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void resubmitIncompletePublications() {
        moveExhaustedPublicationsToDeadLetter();
        countAttemptsOfPendingPublications();
        incompleteEventPublications.resubmitIncompletePublicationsOlderThan(MINIMUM_AGE);
        forgetAttemptsOfFinishedPublications();
        reportStuckPublications();
    }

    void moveExhaustedPublicationsToDeadLetter() {
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

    void countAttemptsOfPendingPublications() {
        jdbcTemplate.update(COUNT_ATTEMPTS, MINIMUM_AGE.toSeconds());
    }

    void forgetAttemptsOfFinishedPublications() {
        jdbcTemplate.update("delete from event_publication_attempt where publication_id not in (select id from event_publication)");
    }

    void reportStuckPublications() {
        Long stuck = jdbcTemplate.queryForObject(
                "select count(*) from event_publication where completion_date is null and publication_date < now() - make_interval(secs => ?)",
                Long.class, STUCK_AFTER.toSeconds());
        if (stuck != null && stuck > 0) {
            log.error("Event publications incomplete for more than {}: count={}", STUCK_AFTER, stuck);
        }
    }
}
