package com.red_escuela.backend.shared.audit.service;

import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "app.audit.archive.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogArchiveJob {

    private final AuditLogRepository auditLogRepository;
    private final RetentionSettings settings;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final int archiveAfterMonths;

    public AuditLogArchiveJob(AuditLogRepository auditLogRepository, RetentionSettings settings, Clock clock,
                              PlatformTransactionManager transactionManager,
                              @Value("${app.audit.archive.months:12}") int archiveAfterMonths) {
        if (archiveAfterMonths < 1) {
            throw new IllegalArgumentException("app.audit.archive.months debe ser mayor o igual a 1");
        }
        this.auditLogRepository = auditLogRepository;
        this.settings = settings;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.archiveAfterMonths = archiveAfterMonths;
    }

    @Scheduled(cron = "0 35 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "AuditLogArchiveJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        archiveExpiredAuditEntries();
    }

    public int archiveExpiredAuditEntries() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMonths(archiveAfterMonths);
        int total = 0;
        for (int batch = 0; batch < settings.maxBatchesPerRun(); batch++) {
            Integer moved = transaction.execute(status -> auditLogRepository.archiveOlderThan(cutoff, settings.batchSize()));
            int count = moved == null ? 0 : moved;
            total += count;
            if (count < settings.batchSize()) {
                break;
            }
        }
        return total;
    }
}
