package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentAllocation;

import com.red_escuela.backend.shared.domain.Money;

public record PaymentAllocationResponse(Long invoiceId, Money amountApplied) {

    public static PaymentAllocationResponse from(PaymentAllocation allocation) {
        return new PaymentAllocationResponse(allocation.getInvoice().getId(), allocation.getAmountApplied());
    }
}
