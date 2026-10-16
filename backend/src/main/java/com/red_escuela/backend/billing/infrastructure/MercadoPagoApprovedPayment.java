package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.operation.ReportedGatewayAllocation;

public record MercadoPagoApprovedPayment(ReportedGatewayAllocation reportedAllocation, Integer installments, CardPaymentType cardType) {

    public MercadoPagoApprovedPayment(ReportedGatewayAllocation reportedAllocation, Integer installments) {
        this(reportedAllocation, installments, null);
    }
}
