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
            Long programId = count.getProgramId();
            boolean configured = isConfigured(programId, month);
            BillingConfigurationAlert alert = alertRepository.findByProgramIdAndPeriod(programId, month).orElse(null);
            if (configured) {
                if (alert != null && alert.isOpen()) {
                    alert.setResolvedAt(now);
                    alertRepository.save(alert);
                }
                continue;
            }
            if (alert == null) {
                alert = new BillingConfigurationAlert();
                alert.setProgram(programRepository.getReferenceById(programId));
                alert.setPeriod(month);
                alert.setDetectedAt(now);
            }
            alert.setAffectedEnrollments(count.getTotal().intValue());
            alert.setLastDetectedAt(now);
            alert.setResolvedAt(null);
            alertRepository.save(alert);
            log.warn("Año lectivo sin configurar: programa={}, mes={}, inscripciones afectadas={}", programId, month, count.getTotal());
        }
    }

    @Transactional
    public void resolveCoveredAlerts(Long programId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (BillingConfigurationAlert alert : alertRepository.findByProgramIdAndResolvedAtIsNull(programId)) {
            if (isConfigured(programId, alert.getPeriod())) {
                alert.setResolvedAt(now);
                alertRepository.save(alert);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<BillingConfigurationAlert> listOpen() {
        return alertRepository.findByResolvedAtIsNullOrderByPeriodDesc();
    }

    private boolean isConfigured(Long programId, YearMonth month) {
        YearCycles cycles = new YearCycles(templateRepository.findAllByProgramId(programId).stream().map(t -> YearCycle.of(t, month.getYear())).toList(), null);
        return cycles.covering(month).isPresent();
    }
}
