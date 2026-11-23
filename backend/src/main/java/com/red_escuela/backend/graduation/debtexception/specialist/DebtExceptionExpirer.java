package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
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
        for (Long id : repository.findDueIds(now, PageRequest.of(0, BATCH_SIZE))) {
            if (repository.markExpiredIfDue(id, now) == 1) {
                auditRecorder.expired(id);
                expired++;
            }
        }
        return expired;
    }
}
