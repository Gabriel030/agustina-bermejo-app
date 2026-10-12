package com.red_escuela.backend.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ProcessedEffectGuard {
    private static final Logger log = LoggerFactory.getLogger(ProcessedEffectGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEffectGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean runEffectOnce(DeduplicableDomainEvent event, String handlerName, Runnable effect) {
        if (event.eventId() == null) {
            effect.run();
            return true;
        }
        int claimed = jdbcTemplate.update(
                "insert into processed_effect (event_id, handler_name, processed_at) values (?, ?, now()) on conflict do nothing",
                event.eventId(), handlerName);
        if (claimed == 0) {
            log.info("Effect skipped, already processed: handler={}, eventId={}", handlerName, event.eventId());
            return false;
        }
        effect.run();
        return true;
    }
}
