package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeExpirer;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeExpiryResult;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class AdvanceEnrollmentFeeExpiryBillingRunStep extends AbstractBillingRunStep {
    public static final String ADVANCE_ENROLLMENT_FEE_EXPIRY_FAILED = "ADVANCE_ENROLLMENT_FEE_EXPIRY_FAILED";
    private static final String FAILURE_MESSAGE = "No se pudo vencer la matrícula anticipada";

    private final AdvanceEnrollmentFeeExpirer expirer;

    public AdvanceEnrollmentFeeExpiryBillingRunStep(AdvanceEnrollmentFeeExpirer expirer) {
        this.expirer = expirer;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.ADVANCE_ENROLLMENT_FEE_EXPIRY;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        AdvanceEnrollmentFeeExpiryResult result = expirer.expireFeesWhoseWindowClosedBefore(businessDate);
        List<BillingRunItemRecord> failures = result.failedInvoiceIds().stream().map(AdvanceEnrollmentFeeExpiryBillingRunStep::failureOf).toList();
        return new BillingRunStepOutcome(result.candidates(), result.expired(), result.skipped(), failures.size(), failures);
    }

    private static BillingRunItemRecord failureOf(Long invoiceId) {
        return BillingRunItemRecord.failedSubject(BillingRunSubjectType.INVOICE, invoiceId, ADVANCE_ENROLLMENT_FEE_EXPIRY_FAILED, FAILURE_MESSAGE);
    }
}
