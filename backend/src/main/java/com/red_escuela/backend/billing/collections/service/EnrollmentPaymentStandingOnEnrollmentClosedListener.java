package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentClosedWhileSuspendedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EnrollmentPaymentStandingOnEnrollmentClosedListener {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentPaymentStandingOnEnrollmentClosedListener.class);

    private final EnrollmentPaymentStandingService standingService;

    public EnrollmentPaymentStandingOnEnrollmentClosedListener(EnrollmentPaymentStandingService standingService) {
        this.standingService = standingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void recordWithdrawalForNonpayment(EnrollmentClosedWhileSuspendedEvent event) {
        try {
            standingService.recordTermClosedWhileSuspended(event.kind(), event.enrollmentId(), event.businessDate());
        } catch (RuntimeException failure) {
            log.warn("No se pudo registrar la baja por mora en el estado de cobranza: tipo={}, inscripcion={}", event.kind(), event.enrollmentId(), failure);
        }
    }
}
