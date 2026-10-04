package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.exception.NoActivePaymentPlanException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import com.red_escuela.backend.billing.planning.ChargePlan;
import com.red_escuela.backend.billing.planning.ChargeScope;
import com.red_escuela.backend.billing.planning.MonthlyChargePlanner;

@Service
public class InvoiceGenerationService {
    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceFactory invoiceFactory;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ChargePlanningContextLoader contextLoader;
    private final MonthlyChargePlanner planner;
    private final ChargePlanExecutor planExecutor;
    private final Clock clock;
    private final int recurringFeeDueDays;

    public InvoiceGenerationService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, InvoiceRepository invoiceRepository, InvoiceFactory invoiceFactory,
                                     RecurringFeeWindowService recurringFeeWindowService,
                                     ProgramEnrollmentService programEnrollmentService, ChargePlanningContextLoader contextLoader,
                                     MonthlyChargePlanner planner, ChargePlanExecutor planExecutor, Clock clock,
                                     @org.springframework.beans.factory.annotation.Value("${app.billing.recurring-fee-due-days:10}") int recurringFeeDueDays) {
        this.recurringFeeDueDays = recurringFeeDueDays;
        this.contextLoader = contextLoader;
        this.planner = planner;
        this.planExecutor = planExecutor;
        this.clock = clock;
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceFactory = invoiceFactory;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.programEnrollmentService = programEnrollmentService;
    }

    @Transactional
    public void generateNextInstallmentForCourse(CourseEnrollment courseEnrollment, YearMonth today) {
        if (isSuspendedOrWithdrawnForNonPayment(courseEnrollment) || courseEnrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            return;
        }
        PaymentPlanSelection selection = paymentPlanSelectionRepository.findActiveForCourseEnrollment(courseEnrollment.getId(), today)
                .orElseThrow(() -> new NoActivePaymentPlanException("No active payment plan for course enrollment " + courseEnrollment.getId()));
        generateByFrequency(selection, courseEnrollment, null, today);
    }

    @Transactional
    public void generateNextInstallmentForProgram(ProgramEnrollment programEnrollment, YearMonth today) {
        if (!isBillableProgramEnrollment(programEnrollment)) {
            return;
        }
        PaymentPlanSelection selection = paymentPlanSelectionRepository.findActiveForProgramEnrollment(programEnrollment.getId(), today)
                .orElseThrow(() -> new NoActivePaymentPlanException("No active payment plan for program enrollment " + programEnrollment.getId()));
        if (selection.getPaymentPlanOption().getCadence() == PaymentPlanCadence.MONTHLY) {
            executePlan(programEnrollment, today, ChargeScope.RECURRING_ONLY);
            return;
        }
        generateByFrequency(selection, null, programEnrollment, today);
    }

    @Transactional
    public void generateEnrollmentFeeForProgram(ProgramEnrollment programEnrollment, YearMonth today) {
        executePlan(programEnrollment, today, ChargeScope.ENROLLMENT_FEE_ONLY);
    }

    @Transactional
    public void generateChargesForProgram(ProgramEnrollment programEnrollment, YearMonth month, LocalDate today) {
        executePlan(programEnrollment, month, today, ChargeScope.ALL, true);
    }

    private void executePlan(ProgramEnrollment programEnrollment, YearMonth month, ChargeScope scope) {
        executePlan(programEnrollment, month, LocalDate.now(clock), scope);
    }

    private void executePlan(ProgramEnrollment programEnrollment, YearMonth month, LocalDate today, ChargeScope scope) {
        executePlan(programEnrollment, month, today, scope, false);
    }

    private void executePlan(ProgramEnrollment programEnrollment, YearMonth month, LocalDate today, ChargeScope scope, boolean inCurrentTransaction) {
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(programEnrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), scope, inCurrentTransaction);
        planExecutor.execute(programEnrollment, context.selection(), plan, inCurrentTransaction);
    }

    private boolean isBillableProgramEnrollment(ProgramEnrollment programEnrollment) {
        boolean notAcademicallyActive = programEnrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED
                && programEnrollment.getStatus() != ProgramEnrollmentStatus.PAUSED;
        return !isSuspendedOrWithdrawnForNonPayment(programEnrollment) && !notAcademicallyActive
                && !hasCompletedTheStudyPlan(programEnrollment)
                && !programEnrollmentService.hasOnlyFinalExamsLeft(programEnrollment.getId());
    }

    private boolean hasCompletedTheStudyPlan(ProgramEnrollment programEnrollment) {
        return programEnrollmentService.calculateProgress(programEnrollment.getId()).elegibleParaCompletar();
    }

    private boolean isSuspendedOrWithdrawnForNonPayment(BillableEnrollment billable) {
        return billable.getBillingStanding() == EnrollmentBillingStanding.SUSPENDED
                || billable.getBillingStanding() == EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT;
    }

    private void generateByFrequency(PaymentPlanSelection selection, CourseEnrollment courseEnrollment, ProgramEnrollment programEnrollment, YearMonth today) {
        if (selection.getPaymentPlanOption().getCadence() == PaymentPlanCadence.MONTHLY) {
            createOne(courseEnrollment, programEnrollment, selection, today, LocalDate.now(clock).plusDays(recurringFeeDueDays));
            return;
        }
        if (today.equals(selection.getEffectiveFrom())) {
            YearMonth end = selection.getEffectiveFrom().plusMonths(selection.getScopeMonths() - 1L);
            for (YearMonth month = selection.getEffectiveFrom(); !month.isAfter(end); month = month.plusMonths(1)) {
                createOne(courseEnrollment, programEnrollment, selection, month, LocalDate.now(clock).plusDays(recurringFeeDueDays));
            }
        }
    }

    private void createOne(CourseEnrollment courseEnrollment, ProgramEnrollment programEnrollment, PaymentPlanSelection selection, YearMonth period, LocalDate dueDate) {
        boolean canBeBilled = courseEnrollment != null
                ? recurringFeeWindowService.canBeBilled(courseEnrollment, period)
                : recurringFeeWindowService.canBeBilled(programEnrollment, period);
        if (!canBeBilled) {
            return;
        }
        Invoice invoice = courseEnrollment != null
                ? invoiceFactory.createForCourse(courseEnrollment, selection, FeeType.RECURRING_FEE, period, dueDate)
                : invoiceFactory.createForProgram(programEnrollment, selection, FeeType.RECURRING_FEE, period, dueDate);
    }
}
