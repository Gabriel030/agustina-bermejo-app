package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.events.NightlyBillingRunCompletedWithErrorsEvent;
import com.red_escuela.backend.billing.events.NightlyBillingRunFailedEvent;
import com.red_escuela.backend.billing.events.NightlyBillingRunMissedEvent;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class BillingRunAlertPublisher {
    static final String FAILED_ALERT = "BillingRunAlertPublisher.publishRunFailedAlert";
    static final String MISSED_ALERT = "BillingRunAlertPublisher.publishRunMissedAlert";
    static final String WITH_ERRORS_ALERT = "BillingRunAlertPublisher.publishRunCompletedWithErrorsAlert";

    private final ApplicationEventPublisher eventPublisher;
    private final ProcessedEffectGuard effectGuard;

    public BillingRunAlertPublisher(ApplicationEventPublisher eventPublisher, ProcessedEffectGuard effectGuard) {
        this.eventPublisher = eventPublisher;
        this.effectGuard = effectGuard;
    }

    @Transactional
    public void publishRunFailedAlert(Long nightlyRunId, LocalDate businessDate, String failedStep, String failureMessage) {
        publishOnce(NightlyBillingRunFailedEvent.failedAt(nightlyRunId, businessDate, failedStep, failureMessage), FAILED_ALERT);
    }

    @Transactional
    public void publishRunMissedAlert(LocalDate businessDate) {
        publishOnce(NightlyBillingRunMissedEvent.missedOn(businessDate), MISSED_ALERT);
    }

    @Transactional
    public void publishRunCompletedWithErrorsAlert(Long nightlyRunId, LocalDate businessDate) {
        publishOnce(NightlyBillingRunCompletedWithErrorsEvent.completedWithErrors(nightlyRunId, businessDate), WITH_ERRORS_ALERT);
    }

    private void publishOnce(DeduplicableDomainEvent event, String alertName) {
        effectGuard.runEffectOnce(event, alertName, () -> eventPublisher.publishEvent(event));
    }
}
