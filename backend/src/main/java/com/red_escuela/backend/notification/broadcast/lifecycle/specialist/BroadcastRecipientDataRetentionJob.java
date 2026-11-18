package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.broadcast.retention.enabled", havingValue = "true", matchIfMissing = true)
public class BroadcastRecipientDataRetentionJob {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final AuditTrail auditTrail;
    private final RetentionSettings settings;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final int retentionMonths;

    public BroadcastRecipientDataRetentionJob(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository,
                                              AuditTrail auditTrail, RetentionSettings settings, Clock clock,
                                              PlatformTransactionManager transactionManager,
                                              @Value("${app.broadcast.retention.months:12}") int retentionMonths) {
        if (retentionMonths < 1) {
            throw new IllegalArgumentException("app.broadcast.retention.months debe ser mayor o igual a 1");
        }
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
        this.auditTrail = auditTrail;
        this.settings = settings;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.retentionMonths = retentionMonths;
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "BroadcastRecipientDataRetentionJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        anonymizeExpiredRecipientData();
    }

    public int anonymizeExpiredRecipientData() {
        Instant now = clock.instant();
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(retentionMonths).toInstant();
        int broadcasts = 0;
        for (int round = 0; round < settings.maxBatchesPerRun(); round++) {
            List<Long> batch = broadcastRepository.findIdsWithRecipientDataPastRetention(cutoff, PageRequest.ofSize(settings.batchSize()));
            batch.forEach(broadcastId -> transaction.executeWithoutResult(status -> anonymizeRecipients(broadcastId, now)));
            broadcasts += batch.size();
            if (batch.size() < settings.batchSize()) {
                break;
            }
        }
        return broadcasts;
    }

    private void anonymizeRecipients(Long broadcastId, Instant now) {
        int anonymized = recipientRepository.anonymizeByBroadcastId(broadcastId, now);
        if (anonymized > 0) {
            auditTrail.record(new AuditRecord(AuditAction.BROADCAST_RECIPIENT_DATA_ANONYMIZED, AuditEntityType.BROADCAST,
                    broadcastId, null, String.valueOf(anonymized), "RETENTION_" + retentionMonths + "_MONTHS"));
        }
    }
}
