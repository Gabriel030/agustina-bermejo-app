package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.entity.CardType;

public record MercadoPagoApprovedPayment(PaymentAllocationRequest reportedAllocation, Integer installments, CardType cardType) {

    public MercadoPagoApprovedPayment(PaymentAllocationRequest reportedAllocation, Integer installments) {
        this(reportedAllocation, installments, null);
    }
}
