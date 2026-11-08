package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.SuspendedEnrollmentClosureReport;
import com.red_escuela.backend.billing.collections.service.CourseEnrollmentWithdrawalAtCourseEndService;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.service.SuspendedEnrollmentClosureService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.stream.Stream;

@Component
public class TermEndWithdrawalBillingRunStep extends AbstractBillingRunStep {
    private final SuspendedEnrollmentClosureService programClosureService;
    private final CourseEnrollmentWithdrawalAtCourseEndService courseWithdrawalService;

    public TermEndWithdrawalBillingRunStep(SuspendedEnrollmentClosureService programClosureService,
                                           CourseEnrollmentWithdrawalAtCourseEndService courseWithdrawalService) {
        this.programClosureService = programClosureService;
        this.courseWithdrawalService = courseWithdrawalService;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.TERM_END_WITHDRAWAL;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        SuspendedEnrollmentClosureReport programs = emptyReport();
        SuspendedEnrollmentClosureReport courses = emptyReport();
        RuntimeException programFailure = null;
        RuntimeException courseFailure = null;
        try {
            programs = programClosureService.closeFinishedCyclesReportingFailures();
        } catch (RuntimeException e) {
            programFailure = e;
        }
        try {
            courses = courseWithdrawalService.withdrawSuspendedEnrollmentsOfEndedCourses();
        } catch (RuntimeException e) {
            courseFailure = e;
        }
        if (programFailure != null || courseFailure != null) {
            RuntimeException first = programFailure != null ? programFailure : courseFailure;
            if (programFailure != null && courseFailure != null) {
                first.addSuppressed(courseFailure);
            }
            throw first;
        }
        return BillingRunStepOutcome.ofCandidates(programs.candidates() + courses.candidates(),
                Stream.concat(programs.failures().stream(), courses.failures().stream()).map(TermEndWithdrawalBillingRunStep::itemOf).toList());
    }

    private static SuspendedEnrollmentClosureReport emptyReport() {
        return new SuspendedEnrollmentClosureReport(0, 0, java.util.List.of());
    }

    private static BillingRunItemRecord itemOf(SuspendedEnrollmentClosureReport.EnrollmentClosureFailure failure) {
        BillingRunSubjectType subjectType = failure.kind() == EnrollmentKind.COURSE ? BillingRunSubjectType.COURSE_ENROLLMENT : BillingRunSubjectType.PROGRAM_ENROLLMENT;
        return failure.rejectedByRule()
                ? BillingRunItemRecord.skippedSubject(subjectType, failure.enrollmentId(), failure.code(), failure.message())
                : BillingRunItemRecord.failedSubject(subjectType, failure.enrollmentId(), failure.code(), failure.message());
    }
}
