package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
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
import com.red_escuela.backend.billing.planning.ChargePlan;
import com.red_escuela.backend.billing.planning.ChargeScope;
import com.red_escuela.backend.billing.planning.MonthlyChargePlanner;

@Service
public class MonthlyInstallmentRowProcessor {
    private final InvoiceGenerationService invoiceGenerationService;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ChargePlanningContextLoader contextLoader;
    private final MonthlyChargePlanner planner;
    private final ChargePlanExecutor planExecutor;
    private final Clock clock;

    public MonthlyInstallmentRowProcessor(InvoiceGenerationService invoiceGenerationService, InvoiceRepository invoiceRepository,
                                           ProgramEnrollmentRepository programEnrollmentRepository,
                                           CourseEnrollmentRepository courseEnrollmentRepository,
                                           ChargePlanningContextLoader contextLoader, MonthlyChargePlanner planner,
                                           ChargePlanExecutor planExecutor, Clock clock) {
        this.contextLoader = contextLoader;
        this.planner = planner;
        this.planExecutor = planExecutor;
        this.clock = clock;
        this.invoiceGenerationService = invoiceGenerationService;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForProgramEnrollment(Long enrollmentId, YearMonth month) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        LocalDate today = month.equals(YearMonth.now(clock)) ? LocalDate.now(clock) : month.atDay(1);
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.ALL);
        return outcome(planExecutor.execute(enrollment, context.selection(), plan) > 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForProgramEnrollmentCatchingUp(Long enrollmentId, YearMonth month) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        LocalDate today = month.equals(YearMonth.now(clock)) ? LocalDate.now(clock) : month.atDay(1);
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.ALL, true);
        return outcome(planExecutor.execute(enrollment, context.selection(), plan) > 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int issueEnrollmentFeesOnly(Long enrollmentId, YearMonth month, LocalDate today) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.ENROLLMENT_FEE_ONLY, true);
        return planExecutor.execute(enrollment, context.selection(), plan);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForCourseEnrollment(Long enrollmentId, YearMonth month) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(enrollmentId));
        if (invoiceRepository.existsByCourseEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, FeeType.RECURRING_FEE, month)) {
            return InstallmentIssuanceOutcome.SKIPPED;
        }
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, month);
        return outcome(invoiceRepository.existsByCourseEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, FeeType.RECURRING_FEE, month));
    }

    private InstallmentIssuanceOutcome outcome(boolean issued) {
        return issued ? InstallmentIssuanceOutcome.ISSUED : InstallmentIssuanceOutcome.SKIPPED;
    }
}
