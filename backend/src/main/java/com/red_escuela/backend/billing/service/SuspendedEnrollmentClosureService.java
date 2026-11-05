package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.configuration.service.YearCycleFactory;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.SuspendedEnrollmentClosureReport;
import com.red_escuela.backend.billing.collections.domain.EnrollmentClosedWhileSuspendedEvent;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.billing.collections.repository.EnrollmentPaymentStandingRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.domain.YearCycles;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class SuspendedEnrollmentClosureService {


    private static final Logger log = LoggerFactory.getLogger(SuspendedEnrollmentClosureService.class);
    private static final List<ProgramEnrollmentStatus> ACTIVE = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final EnrollmentPaymentStandingRepository standingRepository;
    private final EnrollmentPaymentStandingQuery standingQuery;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearCycleFactory cycleFactory;
    private final TermRepository termRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final Clock clock;
    private final TransactionTemplate requiresNew;
    private final ApplicationEventPublisher eventPublisher;

    public SuspendedEnrollmentClosureService(ProgramEnrollmentRepository programEnrollmentRepository,
                                             ProgramYearBillingTemplateRepository templateRepository, TermRepository termRepository, InvoiceRepository invoiceRepository,
                                             ProgramEnrollmentService programEnrollmentService, Clock clock,
                                             PlatformTransactionManager transactionManager, YearCycleFactory cycleFactory,
                                             ApplicationEventPublisher eventPublisher,
                                             EnrollmentPaymentStandingRepository standingRepository, EnrollmentPaymentStandingQuery standingQuery) {
        this.standingRepository = standingRepository;
        this.standingQuery = standingQuery;
        this.eventPublisher = eventPublisher;
        this.cycleFactory = cycleFactory;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.templateRepository = templateRepository;
        this.termRepository = termRepository;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.clock = clock;
    }

    public int closeFinishedCycles() {
        return closeFinishedCyclesReportingFailures().closed();
    }

    public SuspendedEnrollmentClosureReport closeFinishedCyclesReportingFailures() {
        List<Long> candidateIds = programEnrollmentRepository.findAllById(standingRepository.findSuspendedProgramEnrollmentIds()).stream()
                .filter(enrollment -> ACTIVE.contains(enrollment.getStatus())).map(ProgramEnrollment::getId).sorted().toList();
        int closed = 0;
        List<SuspendedEnrollmentClosureReport.EnrollmentClosureFailure> failures = new ArrayList<>();
        for (Long id : candidateIds) {
            try {
                if (Boolean.TRUE.equals(requiresNew.execute(status -> closeIfCycleEnded(id)))) {
                    closed++;
                }
            } catch (RuntimeException e) {
                logClosureFailure(id, e);
                failures.add(closureFailureOf(id, e));
            }
        }
        return new SuspendedEnrollmentClosureReport(candidateIds.size(), closed, failures);
    }

    private SuspendedEnrollmentClosureReport.EnrollmentClosureFailure closureFailureOf(Long enrollmentId, RuntimeException failure) {
        if (failure instanceof ReportsPolicyViolations rejection) {
            String codes = rejection.violations().stream().map(PolicyViolation::code).collect(Collectors.joining(","));
            return new SuspendedEnrollmentClosureReport.EnrollmentClosureFailure(enrollmentId, true, "WITHDRAWAL_REJECTED_BY_RULE", codes);
        }
        return new SuspendedEnrollmentClosureReport.EnrollmentClosureFailure(enrollmentId, false, "SUBJECT_PROCESSING_FAILED", failure.getMessage());
    }

    private void logClosureFailure(Long enrollmentId, RuntimeException failure) {
        if (failure instanceof ReportsPolicyViolations rejection) {
            log.warn("Baja por mora rechazada por una regla: inscripcion={}, causa={}", enrollmentId,
                    rejection.violations().stream().map(PolicyViolation::code).toList());
            return;
        }
        log.error("Falló el cierre de la inscripción suspendida: inscripcion={}", enrollmentId, failure);
    }

    private boolean closeIfCycleEnded(Long enrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        LocalDate today = LocalDate.now(clock);
        LocalDate suspendedOn = standingQuery.findStandingOfProgramEnrollment(enrollmentId).map(EnrollmentPaymentStandingView::suspendedSince).orElse(today);
        YearMonth suspensionMonth = YearMonth.from(suspendedOn);
        YearCycles cycles = new YearCycles(cycleFactory.cyclesOf(templateRepository.findAllByProgramId(enrollment.getProgram().getId()), suspensionMonth.getYear()), null);
        Optional<YearCycle> cycle = cycles.covering(suspensionMonth).or(() -> cycles.latestStartedOn(suspensionMonth));
        if (cycle.isEmpty() || !cycleOrTermEnded(enrollment, cycle.get(), suspendedOn, today)) {
            return false;
        }
        programEnrollmentService.withdrawForNonpayment(enrollmentId);
        eventPublisher.publishEvent(EnrollmentClosedWhileSuspendedEvent.closedOn(EnrollmentKind.PROGRAM, enrollmentId, today));
        programEnrollmentRepository.save(enrollment);
        log.info("Inscripción suspendida dada de baja al cierre de su período: inscripcion={}, cierreAnioLectivo={}", enrollmentId, cycle.get().end());
        return true;
    }

    private boolean cycleOrTermEnded(ProgramEnrollment enrollment, YearCycle cycle, LocalDate suspendedOn, LocalDate today) {
        if (cycle.yearTerms().withdrawSuspendedAtTermEnd()) {
            Optional<LocalDate> termEnd = termRepository.findTermsOfProgram(enrollment.getProgram().getId()).stream()
                    .filter(t -> t.getStartDate() != null && t.getEndDate() != null)
                    .filter(t -> !t.getId().equals(cycle.template().termId()))
                    .filter(t -> !suspendedOn.isBefore(t.getStartDate().toLocalDate()) && !suspendedOn.isAfter(t.getEndDate().toLocalDate()))
                    .min(Comparator.comparing(t -> Duration.between(t.getStartDate(), t.getEndDate())))
                    .map(t -> t.getEndDate().toLocalDate());
            if (termEnd.isPresent()) {
                return today.isAfter(termEnd.get());
            }
        }
        return YearMonth.from(today).isAfter(cycle.end());
    }
}
