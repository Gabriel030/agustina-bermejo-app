package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.planning.YearCycle;
import com.red_escuela.backend.billing.planning.YearCycles;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class SuspendedEnrollmentClosureService {

    static final String CLOSURE_REASON = "Baja por falta de pago al cierre del año lectivo: deuda del cohorte cancelada";

    private static final Logger log = LoggerFactory.getLogger(SuspendedEnrollmentClosureService.class);
    private static final List<ProgramEnrollmentStatus> ACTIVE = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final Clock clock;

    public SuspendedEnrollmentClosureService(ProgramEnrollmentRepository programEnrollmentRepository,
                                             ProgramYearBillingTemplateRepository templateRepository, InvoiceRepository invoiceRepository,
                                             ProgramEnrollmentService programEnrollmentService, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.templateRepository = templateRepository;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.clock = clock;
    }

    public int closeFinishedCycles() {
        int closed = 0;
        for (Long id : programEnrollmentRepository.findIdsByBillingStandingAndStatusIn(EnrollmentBillingStanding.SUSPENDED, ACTIVE)) {
            try {
                if (closeIfCycleEnded(id)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.error("Falló el cierre de la inscripción suspendida: inscripcion={}", id, e);
            }
        }
        return closed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean closeIfCycleEnded(Long enrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        LocalDate today = LocalDate.now(clock);
        LocalDate suspendedOn = enrollment.getBillingStandingUpdatedAt() != null ? enrollment.getBillingStandingUpdatedAt().toLocalDate() : today;
        YearMonth suspensionMonth = YearMonth.from(suspendedOn);
        YearCycles cycles = new YearCycles(templateRepository.findAllByProgramId(enrollment.getProgram().getId()).stream()
                .map(t -> YearCycle.of(t, suspensionMonth.getYear())).toList(), null);
        Optional<YearCycle> cycle = cycles.covering(suspensionMonth).or(() -> cycles.latestStartedOn(suspensionMonth));
        if (cycle.isEmpty() || !YearMonth.from(today).isAfter(cycle.get().end())) {
            return false;
        }
        programEnrollmentService.withdraw(enrollmentId);
        enrollment.setBillingStanding(EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);
        enrollment.setBillingStandingUpdatedAt(today.atStartOfDay());
        programEnrollmentRepository.save(enrollment);
        for (Invoice invoice : invoiceRepository.findByProgramEnrollmentIdAndStatusIn(enrollmentId, List.of(InvoiceStatus.PENDING, InvoiceStatus.OVERDUE))) {
            invoice.applyAdjustment(InvoiceAdjustmentType.WAIVE_DEBT, new MonetaryAdjustment.Nominal(invoice.getRemainingBalance()), CLOSURE_REASON, null);
            invoiceRepository.save(invoice);
        }
        log.info("Inscripción suspendida dada de baja al cierre del año lectivo: inscripcion={}, cierre={}", enrollmentId, cycle.get().end());
        return true;
    }
}
