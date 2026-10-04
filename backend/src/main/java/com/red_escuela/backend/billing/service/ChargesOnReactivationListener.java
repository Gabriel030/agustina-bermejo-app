package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.notification.SuspensionLiftedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSuspensionLifted(SuspensionLiftedEvent event) {
        if (!(event.billable() instanceof ProgramEnrollment enrollment)) {
            return;
        }
        try {
            InstallmentIssuanceOutcome outcome = rowProcessor.issueForProgramEnrollmentCatchingUp(enrollment.getId(), YearMonth.now(clock));
            log.info("Cargos del mes al reactivar la inscripción: inscripcion={}, resultado={}", enrollment.getId(), outcome);
        } catch (RuntimeException e) {
            log.error("Falló la emisión de cargos al reactivar la inscripción: inscripcion={}", enrollment.getId(), e);
        }
    }
}
