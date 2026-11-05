package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.collections.domain.SuspendedEnrollmentClosureReport;
import com.red_escuela.backend.billing.service.SuspendedEnrollmentClosureService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TermEndWithdrawalBillingRunStep extends AbstractBillingRunStep {
    private final SuspendedEnrollmentClosureService closureService;

    public TermEndWithdrawalBillingRunStep(SuspendedEnrollmentClosureService closureService) {
        this.closureService = closureService;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.TERM_END_WITHDRAWAL;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        SuspendedEnrollmentClosureReport report = closureService.closeFinishedCyclesReportingFailures();
        return BillingRunStepOutcome.ofCandidates(report.candidates(), report.failures().stream().map(TermEndWithdrawalBillingRunStep::itemOf).toList());
    }

    private static BillingRunItemRecord itemOf(SuspendedEnrollmentClosureReport.EnrollmentClosureFailure failure) {
        return failure.rejectedByRule()
                ? BillingRunItemRecord.skippedSubject(BillingRunSubjectType.PROGRAM_ENROLLMENT, failure.enrollmentId(), failure.code(), failure.message())
                : BillingRunItemRecord.failedSubject(BillingRunSubjectType.PROGRAM_ENROLLMENT, failure.enrollmentId(), failure.code(), failure.message());
    }
}
