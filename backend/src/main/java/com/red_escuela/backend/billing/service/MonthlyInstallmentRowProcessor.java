package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.service.CourseMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;

@Service
public class MonthlyInstallmentRowProcessor {
    private final CourseInvoicePlanningContextLoader courseContextLoader;
    private final CourseMonthlyInvoicePlanner courseMonthlyInvoicePlanner;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final InvoicePlanningContextLoader contextLoader;
    private final ProgramMonthlyInvoicePlanner planner;
    private final PlannedInvoiceIssuingService plannedInvoiceIssuingService;
    private final Clock clock;

    public MonthlyInstallmentRowProcessor(CourseInvoicePlanningContextLoader courseContextLoader, CourseMonthlyInvoicePlanner courseMonthlyInvoicePlanner, InvoiceRepository invoiceRepository,
                                           ProgramEnrollmentRepository programEnrollmentRepository,
                                           CourseEnrollmentRepository courseEnrollmentRepository,
                                           InvoicePlanningContextLoader contextLoader, ProgramMonthlyInvoicePlanner planner,
                                           PlannedInvoiceIssuingService plannedInvoiceIssuingService, Clock clock) {
        this.contextLoader = contextLoader;
        this.planner = planner;
        this.plannedInvoiceIssuingService = plannedInvoiceIssuingService;
        this.clock = clock;
        this.courseContextLoader = courseContextLoader;
        this.courseMonthlyInvoicePlanner = courseMonthlyInvoicePlanner;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForProgramEnrollment(Long enrollmentId, YearMonth month) {
        return outcome(!reportProgramEnrollmentIssuance(enrollmentId, month, LocalDate.now(clock)).issuedInvoiceIds().isEmpty());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForProgramEnrollmentCatchingUp(Long enrollmentId, YearMonth month) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        LocalDate today = LocalDate.now(clock);
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.ALL, true);
        return outcome(plannedInvoiceIssuingService.issuePlannedProgramInvoices(enrollment, context.selection(), plan, InvoiceIssuingReason.REACTIVATION) > 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int issueEnrollmentFeesOnly(Long enrollmentId, YearMonth month, LocalDate today) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.ENROLLMENT_FEE_ONLY, true);
        return plannedInvoiceIssuingService.issuePlannedProgramInvoices(enrollment, context.selection(), plan, InvoiceIssuingReason.YEAR_CONFIGURATION_CHANGE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForCourseEnrollment(Long enrollmentId, YearMonth month) {
        return outcome(!reportCourseEnrollmentIssuance(enrollmentId, month, LocalDate.now(clock)).issuedInvoiceIds().isEmpty());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyIssuanceReport reportProgramEnrollmentIssuance(Long enrollmentId, YearMonth month, LocalDate pricingDate) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, pricingDate);
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.ALL);
        IssuanceTally tally = new IssuanceTally();
        for (PlannedInvoice charge : plan.charges()) {
            tally.add(plannedInvoiceIssuingService.issuePlannedProgramInvoice(enrollment, context.selection(), charge, InvoiceIssuingReason.MONTHLY_RUN, false));
        }
        return tally.reportFor(plan);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyIssuanceReport reportCourseEnrollmentIssuance(Long enrollmentId, YearMonth month, LocalDate pricingDate) {
        CourseInvoicePlanningContextLoader.CourseContext context = courseContextLoader.load(enrollmentId, month, pricingDate);
        MonthlyInvoicePlan plan = courseMonthlyInvoicePlanner.planInvoicesForMonth(context.facts());
        IssuanceTally tally = new IssuanceTally();
        for (PlannedInvoice charge : plan.charges()) {
            tally.add(plannedInvoiceIssuingService.issuePlannedCourseInvoice(context.enrollment(), context.selection(), charge));
        }
        return tally.reportFor(plan);
    }

    private static final class IssuanceTally {
        private final List<Long> issued = new ArrayList<>();
        private final List<Long> alreadyIssued = new ArrayList<>();

        void add(InvoiceIssuingResult result) {
            if (result instanceof InvoiceIssuingResult.InvoiceIssued created) {
                issued.add(created.invoice().getId());
            } else if (result instanceof InvoiceIssuingResult.InvoiceAlreadyIssued existing) {
                alreadyIssued.add(existing.invoiceId());
            }
        }

        MonthlyIssuanceReport reportFor(MonthlyInvoicePlan plan) {
            return new MonthlyIssuanceReport(issued, alreadyIssued, plan.decisions().stream().map(InvoicePlanningDecision::code).toList());
        }
    }

    public record MonthlyIssuanceReport(List<Long> issuedInvoiceIds, List<Long> alreadyIssuedInvoiceIds, List<String> planningDecisionCodes) {
    }

    private InstallmentIssuanceOutcome outcome(boolean issued) {
        return issued ? InstallmentIssuanceOutcome.ISSUED : InstallmentIssuanceOutcome.SKIPPED;
    }
}
