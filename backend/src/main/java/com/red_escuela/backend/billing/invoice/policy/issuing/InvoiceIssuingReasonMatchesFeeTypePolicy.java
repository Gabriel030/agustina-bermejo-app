package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = "FEE_TYPE_NOT_ALLOWED_FOR_REASON", decisions = {"D-050"})
public final class InvoiceIssuingReasonMatchesFeeTypePolicy implements Policy<InvoiceIssuingContext> {

    private static final Set<FeeType> AD_HOC_FEE_TYPES = Set.of(FeeType.EXAM_FEE, FeeType.RETAKEN_SUBJECT_FEE, FeeType.DEGREE_ISSUANCE_FEE, FeeType.ADMINISTRATIVE_FEE);
    private static final Set<FeeType> REGULAR_FEE_TYPES = Set.of(FeeType.MONTHLY_INSTALLMENT, FeeType.ENROLLMENT_FEE);

    @Override
    public String ruleName() {
        return "InvoiceIssuingReasonMatchesFeeTypePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceIssuingContext context) {
        InvoiceIssuingReason reason = context.request().issuingReason();
        FeeType feeType = context.request().feeType();
        boolean allowed = switch (reason) {
            case AD_HOC_CHARGE -> AD_HOC_FEE_TYPES.contains(feeType);
            case PROGRAM_ADMISSION -> feeType == FeeType.ENROLLMENT_FEE;
            case COURSE_ADMISSION -> feeType == FeeType.MONTHLY_INSTALLMENT;
            case MONTHLY_RUN, COURSE_MONTHLY_RUN, PAYMENT_PLAN_SELECTION, REACTIVATION, ENROLLMENT_FEE_ON_DEMAND, YEAR_CONFIGURATION_CHANGE ->
                    REGULAR_FEE_TYPES.contains(feeType);
            case CATCH_UP, ANNUAL_UPFRONT_PAYMENT, LATE_FEE, UPFRONT, LEGACY -> false;
        };
        return allowed ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "FEE_TYPE_NOT_ALLOWED_FOR_REASON", "El tipo de cargo " + feeType + " no se puede emitir por " + reason);
    }
}
