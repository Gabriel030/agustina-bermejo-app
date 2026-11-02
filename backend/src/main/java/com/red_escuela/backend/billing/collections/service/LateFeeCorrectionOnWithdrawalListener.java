package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class LateFeeCorrectionOnWithdrawalListener {
    private static final String HANDLER_NAME = "LateFeeCorrectionOnWithdrawalListener";

    private final LateFeeWithdrawalCorrectionService correctionService;
    private final ProcessedEffectGuard effectGuard;

    public LateFeeCorrectionOnWithdrawalListener(LateFeeWithdrawalCorrectionService correctionService, ProcessedEffectGuard effectGuard) {
        this.correctionService = correctionService;
        this.effectGuard = effectGuard;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @ApplicationModuleListener
    public void correctLateFeesOfWithdrawnEnrollment(EnrollmentWithdrawnForBillingEvent event) {
        effectGuard.runEffectOnce(event, HANDLER_NAME,
                () -> correctionService.correctLateFeesOfEnrollment(event.programEnrollmentId(), event.courseEnrollmentId(), event.registeredOn()));
    }
}
