package com.red_escuela.backend.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

@Component
public class ChargesOnReactivationListener {
    private static final Logger log = LoggerFactory.getLogger(ChargesOnReactivationListener.class);

    private final MonthlyInstallmentRowProcessor rowProcessor;
    private final Clock clock;

    public ChargesOnReactivationListener(MonthlyInstallmentRowProcessor rowProcessor, Clock clock) {
        this.rowProcessor = rowProcessor;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void onSuspensionLifted(EnrollmentSuspensionLiftedEvent event) {
        if (!event.concernsProgramEnrollment()) {
            return;
        }
        InstallmentIssuanceOutcome outcome = rowProcessor.issueForProgramEnrollmentCatchingUp(event.enrollmentId(), YearMonth.now(clock));
        log.info("Cargos del mes al reactivar la inscripción: inscripcion={}, resultado={}", event.enrollmentId(), outcome);
    }
}
