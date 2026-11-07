package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineExpirer;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontExpiryResult;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class AnnualUpfrontExpiryBillingRunStep extends AbstractBillingRunStep {
    public static final String ANNUAL_UPFRONT_EXPIRY_FAILED = "ANNUAL_UPFRONT_EXPIRY_FAILED";
    private static final String FAILURE_MESSAGE = "No se pudo descartar por vencimiento el pedido de pago anual";

    private final AnnualUpfrontDeadlineExpirer expirer;

    public AnnualUpfrontExpiryBillingRunStep(AnnualUpfrontDeadlineExpirer expirer) {
        this.expirer = expirer;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.ANNUAL_UPFRONT_EXPIRY;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        AnnualUpfrontExpiryResult result = expirer.expireRequestsPastDeadline();
        List<BillingRunItemRecord> failures = result.failedRequestIds().stream().map(AnnualUpfrontExpiryBillingRunStep::failureOf).toList();
        int candidates = result.expired() + result.skipped() + failures.size();
        return new BillingRunStepOutcome(candidates, result.expired(), result.skipped(), failures.size(), failures);
    }

    private static BillingRunItemRecord failureOf(Long requestId) {
        return BillingRunItemRecord.failedSubject(BillingRunSubjectType.ANNUAL_UPFRONT_REQUEST, requestId, ANNUAL_UPFRONT_EXPIRY_FAILED, FAILURE_MESSAGE);
    }
}
