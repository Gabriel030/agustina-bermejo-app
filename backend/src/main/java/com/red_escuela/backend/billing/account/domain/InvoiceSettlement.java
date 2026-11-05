package com.red_escuela.backend.billing.account.domain;

import java.time.LocalDate;
import java.util.Objects;

public record InvoiceSettlement(Long allocationId, LocalDate paymentDate, LocalDate allocatedOn) {

    public InvoiceSettlement {
        Objects.requireNonNull(allocationId, "allocationId");
        Objects.requireNonNull(paymentDate, "paymentDate");
        Objects.requireNonNull(allocatedOn, "allocatedOn");
    }
}
