package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentAllocation;

import java.math.BigDecimal;

public record PaymentAllocationResponse(Long invoiceId, BigDecimal amountApplied) {

    public static PaymentAllocationResponse from(PaymentAllocation allocation) {
        return new PaymentAllocationResponse(allocation.getInvoice().getId(), allocation.getAmountApplied());
    }
}
