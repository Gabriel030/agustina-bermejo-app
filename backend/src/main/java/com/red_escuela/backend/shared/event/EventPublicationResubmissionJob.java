package com.red_escuela.backend.shared.event;

import com.red_escuela.backend.shared.mail.MailCircuit;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
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

@Component
@ConditionalOnProperty(name = "app.event-publication.resubmission.enabled", havingValue = "true", matchIfMissing = true)
public class EventPublicationResubmissionJob {
    private static final Logger log = LoggerFactory.getLogger(EventPublicationResubmissionJob.class);
    public static final String MAIL_LISTENER_PREFIX = "com.red_escuela.backend.identity.service.IdentityEmailDispatcher.";
    static final Duration MINIMUM_AGE = Duration.ofMinutes(5);
    static final Duration STUCK_AFTER = Duration.ofHours(1);


    private final IncompleteEventPublications incompleteEventPublications;
    private final JdbcTemplate jdbcTemplate;

    private final EventPublicationAttemptLedger attemptLedger;

    public EventPublicationResubmissionJob(
            IncompleteEventPublications incompleteEventPublications,
            JdbcTemplate jdbcTemplate,
            CircuitBreakerFactory circuitBreakerFactory,
            @Value("${app.event-publication.resubmission.max-attempts:100}") int maxAttempts) {
        this.incompleteEventPublications = incompleteEventPublications;
        this.jdbcTemplate = jdbcTemplate;
        this.attemptLedger = new EventPublicationAttemptLedger(jdbcTemplate, maxAttempts,
                circuitBreakerFactory.breakerNamed(MailCircuit.NAME)::rejectedCount, MAIL_LISTENER_PREFIX);
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M", zone = "${app.timezone}")
    @SchedulerLock(name = "EventPublicationResubmissionJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void resubmitIncompletePublications() {
        attemptLedger.moveExhaustedPublicationsToDeadLetter();
        attemptLedger.countAttemptsOfPendingPublications();
        incompleteEventPublications.resubmitIncompletePublicationsOlderThan(MINIMUM_AGE);
        attemptLedger.forgetAttemptsOfFinishedPublications();
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
