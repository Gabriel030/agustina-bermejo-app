package com.red_escuela.backend.shared.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublicationMetrics {
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public EventPublicationMetrics(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerGauges() {
        Gauge.builder("modulith.events.incomplete", this, EventPublicationMetrics::countIncomplete)
                .description("Event publications without completion date")
                .register(meterRegistry);
        Gauge.builder("modulith.events.oldest.incomplete.seconds", this, EventPublicationMetrics::oldestIncompleteSeconds)
                .baseUnit("seconds")
                .description("Age of the oldest incomplete event publication")
                .register(meterRegistry);
    }

    long countIncomplete() {
        Long count = jdbcTemplate.queryForObject("select count(*) from event_publication where completion_date is null", Long.class);
        return count == null ? 0 : count;
    }

    double oldestIncompleteSeconds() {
        Double seconds = jdbcTemplate.queryForObject(
                "select extract(epoch from (now() - min(publication_date))) from event_publication where completion_date is null", Double.class);
        return seconds == null ? 0 : seconds;
    }
}
