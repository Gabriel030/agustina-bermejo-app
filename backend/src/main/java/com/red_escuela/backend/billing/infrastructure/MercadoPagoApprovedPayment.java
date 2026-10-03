package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;

public record MercadoPagoApprovedPayment(PaymentAllocationRequest reportedAllocation, Integer installments) {
}
