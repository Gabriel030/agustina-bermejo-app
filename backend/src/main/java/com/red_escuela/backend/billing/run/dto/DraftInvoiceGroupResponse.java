package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.run.domain.DraftInvoiceGroup;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.SignedPercentage;

public record DraftInvoiceGroupResponse(MonthlyInvoiceEnrollmentType enrollmentType, String name, FeeType feeType, int draftCount, Money draftTotal,
                                        Money previousPeriodIssuedTotal, Money difference, SignedPercentage differencePercentage) {

    public static DraftInvoiceGroupResponse of(DraftInvoiceGroup group) {
        return new DraftInvoiceGroupResponse(group.key().scope(), group.key().name(), group.key().feeType(), group.draftCount(), group.draftTotal(),
                group.findPreviousIssuedTotal().orElse(null), group.findDifferenceFromPreviousPeriod().orElse(null),
                group.findPercentageDifferenceFromPreviousPeriod().orElse(null));
    }
}
