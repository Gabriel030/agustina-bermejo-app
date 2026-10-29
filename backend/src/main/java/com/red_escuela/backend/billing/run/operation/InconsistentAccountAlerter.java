package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.events.InconsistentAccountDetectedEvent;
import com.red_escuela.backend.billing.run.domain.AccountConsistencyFinding;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class InconsistentAccountAlerter {
    private final ApplicationEventPublisher eventPublisher;

    public InconsistentAccountAlerter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void alertDirectorOfInconsistentAccounts(List<AccountConsistencyFinding> findings, LocalDate businessDate) {
        findings.forEach(finding -> eventPublisher.publishEvent(
                InconsistentAccountDetectedEvent.detectedAt(finding.accountId(), businessDate, finding.findingCode(), finding.detail())));
    }
}
