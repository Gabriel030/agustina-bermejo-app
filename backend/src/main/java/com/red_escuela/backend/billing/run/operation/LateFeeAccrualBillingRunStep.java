package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class LateFeeAccrualBillingRunStep extends AbstractBillingRunStep {
    private final LateFeeAccrualStep accrualStep;

    public LateFeeAccrualBillingRunStep(LateFeeAccrualStep accrualStep) {
        this.accrualStep = accrualStep;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.LATE_FEE_ACCRUAL;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        LateFeeAccrualResult result = accrualStep.accrueLateFees(businessDate);
        return BillingRunStepOutcome.ofCandidates(result.candidates() + result.settlementCandidates(),
                translateReportedErrors(result.errors(), "invoiceId", BillingRunSubjectType.INVOICE));
    }
}
