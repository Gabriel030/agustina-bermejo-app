package com.red_escuela.backend.billing.annualupfront.policy;

import java.time.LocalDate;

public record AnnualUpfrontRequestCheck(boolean enrollmentActive, LocalDate today, LocalDate requestDeadline, boolean scholarshipCoversYear, boolean hasOverdueDebt, Long openRequestInvoiceId,
                                        boolean monthlyInstallmentAlreadyIssued, int lineCount, boolean priceAvailable) {

    public boolean requestAlreadyOpen() {
        return openRequestInvoiceId != null;
    }
}
