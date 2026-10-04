package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
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
import com.red_escuela.backend.billing.planning.CourseChargePlanner;
import com.red_escuela.backend.billing.planning.ChargeScope;
import com.red_escuela.backend.billing.planning.MonthlyChargePlanner;

@Service
public class MonthlyInstallmentRowProcessor {
    private final CourseChargeContextLoader courseContextLoader;
    private final CourseChargePlanner courseChargePlanner;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ChargePlanningContextLoader contextLoader;
    private final MonthlyChargePlanner planner;
    private final ChargePlanExecutor planExecutor;
    private final Clock clock;

    public MonthlyInstallmentRowProcessor(CourseChargeContextLoader courseContextLoader, CourseChargePlanner courseChargePlanner, InvoiceRepository invoiceRepository,
                                           ProgramEnrollmentRepository programEnrollmentRepository,
                                           CourseEnrollmentRepository courseEnrollmentRepository,
                                           ChargePlanningContextLoader contextLoader, MonthlyChargePlanner planner,
                                           ChargePlanExecutor planExecutor, Clock clock) {
        this.contextLoader = contextLoader;
        this.planner = planner;
        this.planExecutor = planExecutor;
        this.clock = clock;
        this.courseContextLoader = courseContextLoader;
        this.courseChargePlanner = courseChargePlanner;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForProgramEnrollment(Long enrollmentId, YearMonth month) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        LocalDate today = LocalDate.now(clock);
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.ALL);
        return outcome(planExecutor.execute(enrollment, context.selection(), plan, IssueReason.MONTHLY_JOB) > 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForProgramEnrollmentCatchingUp(Long enrollmentId, YearMonth month) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        LocalDate today = LocalDate.now(clock);
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.ALL, true);
        return outcome(planExecutor.execute(enrollment, context.selection(), plan, IssueReason.REACTIVATION) > 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int issueEnrollmentFeesOnly(Long enrollmentId, YearMonth month, LocalDate today) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        ChargePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, month, today);
        ChargePlan plan = planner.plan(context.facts(), ChargeScope.ENROLLMENT_FEE_ONLY, true);
        return planExecutor.execute(enrollment, context.selection(), plan, IssueReason.CONFIGURATION_CHANGE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForCourseEnrollment(Long enrollmentId, YearMonth month) {
        CourseChargeContextLoader.CourseContext context = courseContextLoader.load(enrollmentId, month, LocalDate.now(clock));
        ChargePlan plan = courseChargePlanner.plan(context.facts());
        return outcome(planExecutor.executeForCourse(context.enrollment(), context.selection(), plan) > 0);
    }

    private InstallmentIssuanceOutcome outcome(boolean issued) {
        return issued ? InstallmentIssuanceOutcome.ISSUED : InstallmentIssuanceOutcome.SKIPPED;
    }
}
