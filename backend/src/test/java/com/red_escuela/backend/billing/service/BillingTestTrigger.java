package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.service.CourseMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingResult;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
@org.springframework.transaction.annotation.Transactional
public class BillingTestTrigger {
    private final InvoicePlanningContextLoader contextLoader;
    private final CourseInvoicePlanningContextLoader courseContextLoader;
    private final ProgramMonthlyInvoicePlanner planner;
    private final CourseMonthlyInvoicePlanner coursePlanner;
    private final PlannedInvoiceIssuingService executor;
    private final PaymentPlanSelectionInvoiceIssuingService invoiceGenerationService;
    private final Clock clock;
    private final InvoiceIssuingService invoiceIssuingService;

    public BillingTestTrigger(InvoicePlanningContextLoader contextLoader, CourseInvoicePlanningContextLoader courseContextLoader, ProgramMonthlyInvoicePlanner planner,
                              CourseMonthlyInvoicePlanner coursePlanner, PlannedInvoiceIssuingService executor, PaymentPlanSelectionInvoiceIssuingService invoiceGenerationService, Clock clock, InvoiceIssuingService invoiceIssuingService) {
        this.invoiceIssuingService = invoiceIssuingService;
        this.contextLoader = contextLoader;
        this.courseContextLoader = courseContextLoader;
        this.planner = planner;
        this.coursePlanner = coursePlanner;
        this.executor = executor;
        this.invoiceGenerationService = invoiceGenerationService;
        this.clock = clock;
    }

    public void generateNextInstallmentForProgram(ProgramEnrollment enrollment, YearMonth month) {
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, LocalDate.now(clock));
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.MONTHLY_INSTALLMENT_ONLY);
        executor.issuePlannedProgramInvoices(enrollment, context.selection(), plan, InvoiceIssuingReason.MONTHLY_RUN);
    }

    public void generateNextInstallmentForCourse(CourseEnrollment enrollment, YearMonth month) {
        CourseInvoicePlanningContextLoader.CourseContext context = courseContextLoader.load(enrollment.getId(), month, LocalDate.now(clock));
        executor.issuePlannedCourseInvoices(context.enrollment(), context.selection(), coursePlanner.planInvoicesForMonth(context.facts()));
    }

    public void issueInvoicesForNewPaymentPlan(ProgramEnrollment enrollment, YearMonth month, LocalDate today) {
        invoiceGenerationService.issueInvoicesForNewPaymentPlan(enrollment, month, today);
    }

    public void issueEntryFee(ProgramEnrollment enrollment) {
        issueEntryFee(enrollment, null);
    }

    public void issueEntryFee(ProgramEnrollment enrollment, PaymentPlanSelection selection) {
        var cohort = enrollment.getProgramCohort();
        var template = cohort.getTemplate();
        LocalDate today = LocalDate.now(clock);
        YearMonth entryMonth = YearMonth.from(cohort.getCursadaStart());
        int slot = com.red_escuela.backend.billing.planning.domain.YearCycle.of(template, entryMonth.getYear()).slotOf(entryMonth);
        LocalDate due = cohort.getCursadaStart().isBefore(today) ? today.plusDays(template.getLateEnrollmentFeeDueDays()) : cohort.getCursadaStart();
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(new InvoiceRecipient.ProgramEnrollmentRecipient(enrollment), FeeType.ENROLLMENT_FEE, entryMonth, due,
                InvoiceIssuingReason.PAYMENT_PLAN_SELECTION, selection, template.getId(), slot, true, null, null, null, null);
        invoiceIssuingService.issueInvoiceInCallerTransaction(request);
    }

    public Invoice issueInstallment(ProgramEnrollment enrollment, YearMonth period, LocalDate dueDate) {
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(new InvoiceRecipient.ProgramEnrollmentRecipient(enrollment), FeeType.MONTHLY_INSTALLMENT, period, dueDate,
                InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false, null, null, null, null);
        return ((InvoiceIssuingResult.InvoiceIssued) invoiceIssuingService.issueInvoiceInNewTransaction(request)).invoice();
    }
}
