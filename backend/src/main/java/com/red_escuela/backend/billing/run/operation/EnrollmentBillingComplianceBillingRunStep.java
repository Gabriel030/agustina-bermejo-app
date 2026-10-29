package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.domain.EnrollmentComplianceSweepReport;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.service.EnrollmentBillingComplianceSweepService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class EnrollmentBillingComplianceBillingRunStep extends AbstractBillingRunStep {
    private final EnrollmentBillingComplianceSweepService complianceSweep;

    public EnrollmentBillingComplianceBillingRunStep(EnrollmentBillingComplianceSweepService complianceSweep) {
        this.complianceSweep = complianceSweep;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.ENROLLMENT_BILLING_COMPLIANCE;
    }

    @Override
    public Optional<String> findNotApplicableCode(LocalDate businessDate) {
        return complianceSweep.findNotApplicableCode(businessDate);
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        EnrollmentComplianceSweepReport report = complianceSweep.sweepEnrollmentCompliance(businessDate);
        return BillingRunStepOutcome.ofCandidates(report.candidates(), report.failures().stream().map(EnrollmentBillingComplianceBillingRunStep::itemOf).toList());
    }

    private static BillingRunItemRecord itemOf(EnrollmentComplianceSweepReport.EnrollmentComplianceFailure failure) {
        BillingRunSubjectType subjectType = failure.kind() == EnrollmentKind.COURSE ? BillingRunSubjectType.COURSE_ENROLLMENT : BillingRunSubjectType.PROGRAM_ENROLLMENT;
        return BillingRunItemRecord.failedSubject(subjectType, failure.enrollmentId(), SUBJECT_PROCESSING_FAILED, failure.message());
    }
}
