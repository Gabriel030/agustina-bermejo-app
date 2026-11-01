package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.collections.domain.EnrollmentClosedWhileSuspendedEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPaymentStandingOnEnrollmentClosedListener {
    private static final String HANDLER_NAME = "EnrollmentPaymentStandingOnEnrollmentClosedListener";

    private final EnrollmentPaymentStandingService standingService;
    private final ProcessedEffectGuard effectGuard;

    public EnrollmentPaymentStandingOnEnrollmentClosedListener(EnrollmentPaymentStandingService standingService, ProcessedEffectGuard effectGuard) {
        this.standingService = standingService;
        this.effectGuard = effectGuard;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @ApplicationModuleListener
    public void recordWithdrawalForNonpayment(EnrollmentClosedWhileSuspendedEvent event) {
        effectGuard.runEffectOnce(event, HANDLER_NAME,
                () -> standingService.recordTermClosedWhileSuspended(event.kind(), event.enrollmentId(), event.businessDate()));
    }
}
