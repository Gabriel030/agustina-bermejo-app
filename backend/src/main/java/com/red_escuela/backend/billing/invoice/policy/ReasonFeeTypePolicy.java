package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = "FEE_TYPE_NOT_ALLOWED_FOR_REASON", decisions = {"D-050"})
public final class ReasonFeeTypePolicy implements Policy<InvoiceIssuingContext> {
    private static final Set<FeeType> AD_HOC_TYPES = Set.of(FeeType.EXAM_FEE, FeeType.RETAKEN_SUBJECT_FEE, FeeType.DEGREE_ISSUANCE_FEE, FeeType.ADMINISTRATIVE_FEE);
    private static final Set<FeeType> REGULAR_TYPES = Set.of(FeeType.MONTHLY_INSTALLMENT, FeeType.ENROLLMENT_FEE);

    public String ruleName() {
        return "ReasonFeeTypePolicy";
    }

    public PolicyVerdict evaluateRule(InvoiceIssuingContext ctx) {
        InvoiceIssuingReason reason = ctx.request().reason();
        FeeType type = ctx.request().feeType();
        boolean allowed = switch (reason) {
            case LEGACY -> false;
            case AD_HOC_CHARGE -> AD_HOC_TYPES.contains(type);
            case PROGRAM_ADMISSION -> type == FeeType.ENROLLMENT_FEE;
            case COURSE_ADMISSION -> type == FeeType.MONTHLY_INSTALLMENT;
            default -> REGULAR_TYPES.contains(type);
        };
        return allowed ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "FEE_TYPE_NOT_ALLOWED_FOR_REASON", "El tipo de cargo " + type + " no se puede emitir por " + reason);
    }
}
