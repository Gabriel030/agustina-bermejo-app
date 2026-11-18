package com.red_escuela.backend.shared.audit.service;

import com.red_escuela.backend.shared.retention.RetentionBatchPurger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AuditLogRetentionJob {

    private final RetentionBatchPurger purger;
    private final Clock clock;
    private final boolean enabled;
    private final int retentionMonths;

    public AuditLogRetentionJob(RetentionBatchPurger purger, Clock clock,
                                @Value("${app.audit.retention.enabled:false}") boolean enabled,
                                @Value("${app.audit.retention.months:24}") int retentionMonths) {
        if (retentionMonths < 1) {
            throw new IllegalArgumentException("app.audit.retention.months debe ser mayor o igual a 1");
        }
        this.purger = purger;
        this.clock = clock;
        this.enabled = enabled;
        this.retentionMonths = retentionMonths;
    }

    @Scheduled(cron = "0 50 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "AuditLogRetentionJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        deleteExpiredAuditEntries();
    }

    public int deleteExpiredAuditEntries() {
        if (!enabled) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMonths(retentionMonths);
        return purger.purge("audit_log", "audit_log", "occurred_at < :auditCutoff",
                new MapSqlParameterSource("auditCutoff", cutoff));
    }
}
