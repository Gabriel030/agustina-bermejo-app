package com.red_escuela.backend.shared.event;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EventPublicationResubmissionJob {
    private static final Logger log = LoggerFactory.getLogger(EventPublicationResubmissionJob.class);
    static final Duration MINIMUM_AGE = Duration.ofMinutes(5);
    static final Duration STUCK_AFTER = Duration.ofHours(1);

    private final IncompleteEventPublications incompleteEventPublications;
    private final JdbcTemplate jdbcTemplate;

    public EventPublicationResubmissionJob(IncompleteEventPublications incompleteEventPublications, JdbcTemplate jdbcTemplate) {
        this.incompleteEventPublications = incompleteEventPublications;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    @SchedulerLock(name = "EventPublicationResubmissionJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void resubmitIncompletePublications() {
        incompleteEventPublications.resubmitIncompletePublicationsOlderThan(MINIMUM_AGE);
        reportStuckPublications();
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
