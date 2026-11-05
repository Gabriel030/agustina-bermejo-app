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
    private final HeldAccountLookup heldAccounts;

    public LateFeeAccrualBillingRunStep(LateFeeAccrualStep accrualStep, HeldAccountLookup heldAccounts) {
        this.accrualStep = accrualStep;
        this.heldAccounts = heldAccounts;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.LATE_FEE_ACCRUAL;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        LateFeeAccrualResult result = accrualStep.accrueLateFees(businessDate, heldAccounts.heldInvoicesOf(businessDate));
        return BillingRunStepOutcome.ofCandidates(result.candidates() + result.settlementCandidates(),
                translateReportedErrors(result.errors(), "invoiceId", BillingRunSubjectType.INVOICE));
    }
}
