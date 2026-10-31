package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingPreview;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingPreviewer;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;
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
    private final InvoiceIssuingPreviewer invoiceIssuingPreviewer;
    private final Clock clock;

    public MonthlyInstallmentRowProcessor(CourseInvoicePlanningContextLoader courseContextLoader, CourseMonthlyInvoicePlanner courseMonthlyInvoicePlanner, InvoiceRepository invoiceRepository,
                                           ProgramEnrollmentRepository programEnrollmentRepository,
                                           CourseEnrollmentRepository courseEnrollmentRepository,
                                           InvoicePlanningContextLoader contextLoader, ProgramMonthlyInvoicePlanner planner,
                                           PlannedInvoiceIssuingService plannedInvoiceIssuingService, InvoiceIssuingPreviewer invoiceIssuingPreviewer, Clock clock) {
        this.invoiceIssuingPreviewer = invoiceIssuingPreviewer;
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
        return reportProgramEnrollmentIssuance(enrollmentId, month, pricingDate, InvoiceIssuanceDestination.EMIT_NOW);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyIssuanceReport reportProgramEnrollmentIssuance(Long enrollmentId, YearMonth month, LocalDate pricingDate, InvoiceIssuanceDestination destination) {
        ProgramMonthPlan monthPlan = planProgramMonth(enrollmentId, month, pricingDate, LocalDate.now(clock));
        IssuanceTally tally = new IssuanceTally();
        for (PlannedInvoice charge : monthPlan.plan().charges()) {
            tally.add(plannedInvoiceIssuingService.issuePlannedProgramInvoice(monthPlan.enrollment(), monthPlan.selection(), charge, InvoiceIssuingReason.MONTHLY_RUN, false, destination));
        }
        return tally.reportFor(monthPlan.plan());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyIssuanceReport reportCourseEnrollmentIssuance(Long enrollmentId, YearMonth month, LocalDate pricingDate) {
        return reportCourseEnrollmentIssuance(enrollmentId, month, pricingDate, InvoiceIssuanceDestination.EMIT_NOW);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyIssuanceReport reportCourseEnrollmentIssuance(Long enrollmentId, YearMonth month, LocalDate pricingDate, InvoiceIssuanceDestination destination) {
        CourseMonthPlan monthPlan = planCourseMonth(enrollmentId, month, pricingDate, LocalDate.now(clock));
        IssuanceTally tally = new IssuanceTally();
        for (PlannedInvoice charge : monthPlan.plan().charges()) {
            tally.add(plannedInvoiceIssuingService.issuePlannedCourseInvoice(monthPlan.context().enrollment(), monthPlan.context().selection(), charge, destination));
        }
        return tally.reportFor(monthPlan.plan());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyIssuancePreview previewProgramEnrollmentIssuance(Long enrollmentId, YearMonth month, LocalDate pricingDate, LocalDate issueDate) {
        ProgramMonthPlan monthPlan = planProgramMonth(enrollmentId, month, pricingDate, issueDate);
        PreviewTally tally = new PreviewTally();
        for (PlannedInvoice charge : monthPlan.plan().charges()) {
            tally.add(charge.kind(), charge.dueDate(), invoiceIssuingPreviewer.previewInvoiceIssuing(
                    InvoiceIssuingRequestFactory.createProgramEnrollmentRequest(monthPlan.enrollment(), monthPlan.selection(), charge, InvoiceIssuingReason.MONTHLY_RUN)));
        }
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return tally.previewFor(monthPlan.plan());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyIssuancePreview previewCourseEnrollmentIssuance(Long enrollmentId, YearMonth month, LocalDate pricingDate, LocalDate issueDate) {
        CourseMonthPlan monthPlan = planCourseMonth(enrollmentId, month, pricingDate, issueDate);
        PreviewTally tally = new PreviewTally();
        for (PlannedInvoice charge : monthPlan.plan().charges()) {
            tally.add(charge.kind(), charge.dueDate(), invoiceIssuingPreviewer.previewInvoiceIssuing(InvoiceIssuingRequestFactory.createCourseEnrollmentRequest(monthPlan.context().enrollment(),
                    monthPlan.context().selection(), charge, InvoiceIssuingReason.COURSE_MONTHLY_RUN)));
        }
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return tally.previewFor(monthPlan.plan());
    }

    private ProgramMonthPlan planProgramMonth(Long enrollmentId, YearMonth month, LocalDate pricingDate, LocalDate issueDate) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, pricingDate, issueDate);
        return new ProgramMonthPlan(enrollment, context.selection(), planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.ALL));
    }

    private CourseMonthPlan planCourseMonth(Long enrollmentId, YearMonth month, LocalDate pricingDate, LocalDate issueDate) {
        CourseInvoicePlanningContextLoader.CourseContext context = courseContextLoader.load(enrollmentId, month, pricingDate, issueDate);
        return new CourseMonthPlan(context, courseMonthlyInvoicePlanner.planInvoicesForMonth(context.facts()));
    }

    private record ProgramMonthPlan(ProgramEnrollment enrollment, PaymentPlanSelection selection, MonthlyInvoicePlan plan) {
    }

    private record CourseMonthPlan(CourseInvoicePlanningContextLoader.CourseContext context, MonthlyInvoicePlan plan) {
    }

    private static final class PreviewTally {
        private final List<PreviewedCharge> wouldBeIssued = new ArrayList<>();
        private final List<Long> alreadyIssued = new ArrayList<>();

        void add(FeeType kind, LocalDate dueDate, InvoiceIssuingPreview preview) {
            switch (preview) {
                case InvoiceIssuingPreview.InvoiceWouldBeIssued issued -> wouldBeIssued.add(new PreviewedCharge(kind, issued.total(), dueDate));
                case InvoiceIssuingPreview.InvoiceWouldBeAlreadyIssued existing -> alreadyIssued.add(existing.invoiceId());
                case InvoiceIssuingPreview.InvoiceWouldBeRejected rejected -> throw new ChargeRejectedException(rejected.evaluationResult());
            }
        }

        MonthlyIssuancePreview previewFor(MonthlyInvoicePlan plan) {
            return new MonthlyIssuancePreview(wouldBeIssued, alreadyIssued, plan.decisions().stream().map(InvoicePlanningDecision::code).toList());
        }
    }

    public record PreviewedCharge(FeeType kind, NonNegativeMoney total, LocalDate dueDate) {
    }

    public record MonthlyIssuancePreview(List<PreviewedCharge> wouldBeIssued, List<Long> alreadyIssuedInvoiceIds, List<String> planningDecisionCodes) {
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
