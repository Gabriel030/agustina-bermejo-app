package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.entity.BillingConfigurationAlert;
import com.red_escuela.backend.billing.planning.YearCycle;
import com.red_escuela.backend.billing.planning.YearCycles;
import com.red_escuela.backend.billing.repository.BillingConfigurationAlertRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class BillingConfigurationAlertService {
    private static final Logger log = LoggerFactory.getLogger(BillingConfigurationAlertService.class);
    private static final List<ProgramEnrollmentStatus> ACTIVE = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);

    private final BillingConfigurationAlertRepository alertRepository;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramRepository programRepository;
    private final Clock clock;

    public BillingConfigurationAlertService(BillingConfigurationAlertRepository alertRepository,
                                            ProgramYearBillingTemplateRepository templateRepository,
                                            ProgramRepository programRepository, Clock clock) {
        this.alertRepository = alertRepository;
        this.templateRepository = templateRepository;
        this.programRepository = programRepository;
        this.clock = clock;
    }

    @Transactional
    public void refresh(YearMonth month) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (BillingConfigurationAlertRepository.ProgramCount count : alertRepository.countBillableEnrollmentsByProgram(ACTIVE)) {
            YearCycles cycles = cyclesOf(count.getProgramId(), month);
            syncAlert(count, month, BillingConfigurationAlert.REASON_YEAR_NOT_CONFIGURED, !cycles.isConfiguredFor(month), now);
            syncAlert(count, month, BillingConfigurationAlert.REASON_YEAR_GAP, cycles.hasGapAt(month), now);
        }
    }

    private void syncAlert(BillingConfigurationAlertRepository.ProgramCount count, YearMonth month, String reason, boolean active, LocalDateTime now) {
        Long programId = count.getProgramId();
        BillingConfigurationAlert alert = alertRepository.findByProgramIdAndPeriodAndReason(programId, month, reason).orElse(null);
        if (!active) {
            if (alert != null && alert.isOpen()) {
                alert.setResolvedAt(now);
                alertRepository.save(alert);
            }
            return;
        }
        if (alert == null) {
            alert = new BillingConfigurationAlert();
            alert.setProgram(programRepository.getReferenceById(programId));
            alert.setPeriod(month);
            alert.setReason(reason);
            alert.setDetectedAt(now);
        }
        alert.setAffectedEnrollments(count.getTotal().intValue());
        alert.setLastDetectedAt(now);
        alert.setResolvedAt(null);
        alertRepository.save(alert);
        log.warn("Alerta de configuración de cobro: programa={}, mes={}, motivo={}, inscripciones afectadas={}", programId, month, reason, count.getTotal());
    }

    @Transactional
    public void resolveCoveredAlerts(Long programId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (BillingConfigurationAlert alert : alertRepository.findByProgramIdAndResolvedAtIsNull(programId)) {
            if (isResolved(programId, alert)) {
                alert.setResolvedAt(now);
                alertRepository.save(alert);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<BillingConfigurationAlert> listOpen() {
        return alertRepository.findByResolvedAtIsNullOrderByPeriodDesc();
    }

    private boolean isResolved(Long programId, BillingConfigurationAlert alert) {
        YearCycles cycles = cyclesOf(programId, alert.getPeriod());
        return BillingConfigurationAlert.REASON_YEAR_GAP.equals(alert.getReason()) ? !cycles.hasGapAt(alert.getPeriod())
                : cycles.isConfiguredFor(alert.getPeriod());
    }

    private YearCycles cyclesOf(Long programId, YearMonth month) {
        return new YearCycles(templateRepository.findAllByProgramId(programId).stream().map(t -> YearCycle.of(t, month.getYear())).toList(), null);
    }
}
