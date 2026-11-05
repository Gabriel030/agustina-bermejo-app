package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;

public record DraftInvoiceGroupKey(MonthlyInvoiceEnrollmentType scope, String name, FeeType feeType, Currency currency) {

    public DraftInvoiceGroupKey {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(feeType, "feeType");
        Objects.requireNonNull(currency, "currency");
    }
}
