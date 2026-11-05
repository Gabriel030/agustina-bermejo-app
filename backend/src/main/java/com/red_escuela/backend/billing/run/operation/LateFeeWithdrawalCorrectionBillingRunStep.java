package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.operation.LateFeeCorrectionSweepResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeWithdrawalCorrectionStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class LateFeeWithdrawalCorrectionBillingRunStep extends AbstractBillingRunStep {
    private final LateFeeWithdrawalCorrectionStep correctionStep;
    private final HeldAccountLookup heldAccounts;

    public LateFeeWithdrawalCorrectionBillingRunStep(LateFeeWithdrawalCorrectionStep correctionStep, HeldAccountLookup heldAccounts) {
        this.correctionStep = correctionStep;
        this.heldAccounts = heldAccounts;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.LATE_FEE_WITHDRAWAL_CORRECTION;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        LateFeeCorrectionSweepResult result = correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(businessDate, heldAccounts.heldLateFeeChargesOf(businessDate));
        return BillingRunStepOutcome.ofCandidates(result.candidates(),
                translateReportedErrors(result.errors(), "lateFeeChargeId", BillingRunSubjectType.LATE_FEE_CHARGE));
    }
}
