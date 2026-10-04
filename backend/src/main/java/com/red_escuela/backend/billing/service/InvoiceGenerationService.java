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

import java.time.YearMonth;

@Service
public class InvoiceGenerationService {
    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceFactory invoiceFactory;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final ProgramEnrollmentService programEnrollmentService;

    public InvoiceGenerationService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, InvoiceRepository invoiceRepository, InvoiceFactory invoiceFactory,
                                     RecurringFeeWindowService recurringFeeWindowService,
                                     ProgramEnrollmentService programEnrollmentService) {
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
        generateByFrequency(selection, null, programEnrollment, today);
    }

    @Transactional
    public void generateEnrollmentFeeForProgram(ProgramEnrollment programEnrollment, YearMonth today) {
        if (!isBillableProgramEnrollment(programEnrollment)) {
            return;
        }
        if (!programEnrollment.isEnrollmentFeeMonth(today)) {
            return;
        }
        long issuedThisYear = invoiceRepository.countByProgramEnrollmentIdAndFeeTypeAndPeriodBetween(
                programEnrollment.getId(), FeeType.ENROLLMENT_FEE, YearMonth.of(today.getYear(), 1), YearMonth.of(today.getYear(), 12));
        if (issuedThisYear > 0) {
            return;
        }
        PaymentPlanSelection selection = paymentPlanSelectionRepository.findActiveForProgramEnrollment(programEnrollment.getId(), today)
                .orElseThrow(() -> new NoActivePaymentPlanException("No active payment plan for program enrollment " + programEnrollment.getId()));
        invoiceFactory.createForProgram(programEnrollment, selection, FeeType.ENROLLMENT_FEE, today, today.atDay(1));
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
            createOne(courseEnrollment, programEnrollment, selection, today, today.atDay(1));
            return;
        }
        if (today.equals(selection.getEffectiveFrom())) {
            YearMonth end = selection.getEffectiveFrom().plusMonths(selection.getScopeMonths() - 1L);
            for (YearMonth month = selection.getEffectiveFrom(); !month.isAfter(end); month = month.plusMonths(1)) {
                createOne(courseEnrollment, programEnrollment, selection, month, selection.getEffectiveFrom().atDay(1));
            }
        }
    }

    private void createOne(CourseEnrollment courseEnrollment, ProgramEnrollment programEnrollment, PaymentPlanSelection selection, YearMonth period, java.time.LocalDate dueDate) {
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
