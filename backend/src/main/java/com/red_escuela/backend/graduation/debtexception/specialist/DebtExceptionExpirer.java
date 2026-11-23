package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DebtExceptionExpirer {

    private static final int BATCH_SIZE = 200;

    private final DocumentDebtExceptionRepository repository;
    private final DebtExceptionAuditRecorder auditRecorder;
    private final Clock clock;

    public DebtExceptionExpirer(DocumentDebtExceptionRepository repository, DebtExceptionAuditRecorder auditRecorder, Clock clock) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public int expireDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expired = 0;
        int expiredInBatch;
        int batchSize;
        do {
            List<Long> dueIds = repository.findDueIds(now, PageRequest.of(0, BATCH_SIZE));
            batchSize = dueIds.size();
            expiredInBatch = 0;
            for (Long id : dueIds) {
                if (repository.markExpiredIfDue(id, now) == 1) {
                    auditRecorder.expired(id);
                    expiredInBatch++;
                }
            }
            expired += expiredInBatch;
        } while (batchSize == BATCH_SIZE && expiredInBatch > 0);
        return expired;
    }
}
