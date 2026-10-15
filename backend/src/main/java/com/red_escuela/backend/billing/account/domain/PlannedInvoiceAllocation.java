package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record PlannedInvoiceAllocation(Long invoiceId, NonNegativeMoney amount) {

    public PlannedInvoiceAllocation {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(amount, "amount");
    }
}
