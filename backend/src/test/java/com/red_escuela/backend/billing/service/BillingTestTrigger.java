package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.planning.ChargePlan;
import com.red_escuela.backend.billing.planning.ChargeScope;
import com.red_escuela.backend.billing.planning.CourseChargePlanner;
import com.red_escuela.backend.billing.planning.MonthlyChargePlanner;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.issuing.ChargeIssuer;
import com.red_escuela.backend.billing.issuing.ChargeRequest;
import com.red_escuela.backend.billing.issuing.ChargeTarget;
import com.red_escuela.backend.billing.issuing.IssueResult;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
@org.springframework.transaction.annotation.Transactional
public class BillingTestTrigger {
    private final ChargePlanningContextLoader contextLoader;
    private final CourseChargeContextLoader courseContextLoader;
    private final MonthlyChargePlanner planner;
    private final CourseChargePlanner coursePlanner;
    private final ChargePlanExecutor executor;
    private final InvoiceGenerationService invoiceGenerationService;
    private final Clock clock;
    private final ChargeIssuer chargeIssuer;

    public BillingTestTrigger(ChargePlanningContextLoader contextLoader, CourseChargeContextLoader courseContextLoader, MonthlyChargePlanner planner,
                              CourseChargePlanner coursePlanner, ChargePlanExecutor executor, InvoiceGenerationService invoiceGenerationService, Clock clock, ChargeIssuer chargeIssuer) {
        this.chargeIssuer = chargeIssuer;
        this.contextLoader = contextLoader;
        this.courseContextLoader = courseContextLoader;
        this.planner = planner;
        this.coursePlanner = coursePlanner;
        this.executor = executor;
        this.invoiceGenerationService = invoiceGenerationService;
        this.clock = clock;
    }

    public void generateNextInstallmentForProgram(ProgramEnrollment enrollment, YearMonth month) {
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, LocalDate.now(clock));
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.RECURRING_ONLY);
        executor.execute(enrollment, context.selection(), plan, IssueReason.MONTHLY_JOB);
    }

    public void generateNextInstallmentForCourse(CourseEnrollment enrollment, YearMonth month) {
        CourseChargeContextLoader.CourseContext context = courseContextLoader.load(enrollment.getId(), month, LocalDate.now(clock));
        executor.executeForCourse(context.enrollment(), context.selection(), coursePlanner.plan(context.facts()));
    }

    public void generateChargesForProgram(ProgramEnrollment enrollment, YearMonth month, LocalDate today) {
        invoiceGenerationService.generateChargesForProgram(enrollment, month, today);
    }

    public void issueEntryFee(ProgramEnrollment enrollment) {
        issueEntryFee(enrollment, null);
    }

    public void issueEntryFee(ProgramEnrollment enrollment, PaymentPlanSelection selection) {
        var cohort = enrollment.getProgramCohort();
        var template = cohort.getTemplate();
        LocalDate today = LocalDate.now(clock);
        YearMonth entryMonth = YearMonth.from(cohort.getCursadaStart());
        int slot = com.red_escuela.backend.billing.planning.YearCycle.of(template, entryMonth.getYear()).slotOf(entryMonth);
        LocalDate due = cohort.getCursadaStart().isBefore(today) ? today.plusDays(template.getLateEnrollmentFeeDueDays()) : cohort.getCursadaStart();
        ChargeRequest request = new ChargeRequest(new ChargeTarget.ProgramStudent(enrollment), FeeType.ENROLLMENT_FEE, entryMonth, due,
                IssueReason.PLAN_SELECTION, selection, template.getId(), slot, true, null, null, null, null);
        chargeIssuer.issueInCurrentTransaction(request);
    }

    public Invoice issueInstallment(ProgramEnrollment enrollment, YearMonth period, LocalDate dueDate) {
        ChargeRequest request = new ChargeRequest(new ChargeTarget.ProgramStudent(enrollment), FeeType.RECURRING_FEE, period, dueDate,
                IssueReason.MONTHLY_JOB, null, null, null, false, null, null, null, null);
        return ((IssueResult.Issued) chargeIssuer.issueInOwnTransaction(request)).invoice();
    }
}
