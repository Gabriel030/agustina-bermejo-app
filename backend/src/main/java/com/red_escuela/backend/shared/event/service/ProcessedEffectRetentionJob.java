package com.red_escuela.backend.shared.event.service;

import com.red_escuela.backend.shared.retention.RetentionBatchPurger;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class ProcessedEffectRetentionJob {

    private final RetentionBatchPurger purger;
    private final RetentionSettings settings;
    private final Clock clock;
    private final int processedEffectMonths;

    public ProcessedEffectRetentionJob(RetentionBatchPurger purger, RetentionSettings settings, Clock clock,
                                       @Value("${app.retention.processed-effect-months:6}") int processedEffectMonths) {
        if (processedEffectMonths < 1) {
            throw new IllegalArgumentException("app.retention.processed-effect-months debe ser mayor o igual a 1");
        }
        this.purger = purger;
        this.settings = settings;
        this.clock = clock;
        this.processedEffectMonths = processedEffectMonths;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ProcessedEffectRetentionJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        deleteExpiredProcessedEffects();
    }

    public int deleteExpiredProcessedEffects() {
        if (!settings.enabled()) {
            return 0;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusMonths(processedEffectMonths);
        return purger.purge("processed_effect", "processed_effect", "event_id, handler_name", "processed_at < :cutoff",
                new MapSqlParameterSource("cutoff", cutoff));
    }
}
