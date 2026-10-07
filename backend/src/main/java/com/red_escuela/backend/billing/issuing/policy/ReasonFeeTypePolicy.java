package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = "FEE_TYPE_NOT_ALLOWED_FOR_REASON", decisions = {"D-050"})
public final class ReasonFeeTypePolicy implements Policy<IssuanceContext> {
    private static final Set<FeeType> AD_HOC_TYPES = Set.of(FeeType.EXAM_RIGHT, FeeType.RETAKEN_SUBJECT_FEE, FeeType.DEGREE_ISSUANCE, FeeType.ADMINISTRATIVE_FEE);
    private static final Set<FeeType> REGULAR_TYPES = Set.of(FeeType.RECURRING_FEE, FeeType.ENROLLMENT_FEE, FeeType.SUBSCRIPTION_FEE);

    public String ruleName() {
        return "ReasonFeeTypePolicy";
    }

    public PolicyVerdict evaluateRule(IssuanceContext ctx) {
        IssueReason reason = ctx.request().reason();
        FeeType type = ctx.request().feeType();
        boolean allowed = switch (reason) {
            case LEGACY -> false;
            case AD_HOC -> AD_HOC_TYPES.contains(type);
            case ADMISSION -> type == FeeType.ENROLLMENT_FEE;
            case COURSE_ADMISSION -> type == FeeType.RECURRING_FEE;
            default -> REGULAR_TYPES.contains(type);
        };
        return allowed ? PolicyVerdict.allow(name())
                : PolicyVerdict.deny(name(), "FEE_TYPE_NOT_ALLOWED_FOR_REASON", "El tipo de cargo " + type + " no se puede emitir por " + reason);
    }
}
