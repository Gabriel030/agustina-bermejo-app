package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.operation.CardPaymentType;

public record MercadoPagoApprovedPayment(PaymentAllocationRequest reportedAllocation, Integer installments, CardPaymentType cardType) {

    public MercadoPagoApprovedPayment(PaymentAllocationRequest reportedAllocation, Integer installments) {
        this(reportedAllocation, installments, null);
    }
}
