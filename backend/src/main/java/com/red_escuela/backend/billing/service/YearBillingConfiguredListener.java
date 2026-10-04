package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
public class YearBillingConfiguredListener {
    private static final Logger log = LoggerFactory.getLogger(YearBillingConfiguredListener.class);
    private static final List<ProgramEnrollmentStatus> ACTIVE = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final MonthlyInstallmentRowProcessor rowProcessor;
    private final BillingConfigurationAlertService alertService;
    private final Clock clock;

    public YearBillingConfiguredListener(ProgramEnrollmentRepository programEnrollmentRepository, MonthlyInstallmentRowProcessor rowProcessor,
                                         BillingConfigurationAlertService alertService, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.rowProcessor = rowProcessor;
        this.alertService = alertService;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigured(YearBillingConfiguredEvent event) {
        alertService.resolveCoveredAlerts(event.programId());
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        int issued = 0;
        for (Long id : programEnrollmentRepository.findIdsByProgramIdAndStatusIn(event.programId(), ACTIVE)) {
            try {
                issued += rowProcessor.issueEnrollmentFeesOnly(id, month, today);
            } catch (RuntimeException e) {
                log.error("Falló la emisión inmediata de matrícula al configurar el año lectivo: inscripcion={}", id, e);
            }
        }
        log.info("Año lectivo configurado: programa={}, matriculas emitidas de inmediato={}", event.programId(), issued);
    }
}
