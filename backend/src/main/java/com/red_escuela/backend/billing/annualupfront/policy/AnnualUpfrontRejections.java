package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontEligibility;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestRejectedException;

import java.util.List;

public final class AnnualUpfrontRejections {

    private AnnualUpfrontRejections() {
    }

    public static void requireEligible(AnnualUpfrontEligibility eligibility) {
        if (!eligibility.eligible()) {
            throw new AnnualUpfrontRequestRejectedException(List.of(eligibility.violation()));
        }
    }
}
