package com.red_escuela.backend.billing.payment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CashPaymentDetails(String receiptNumber) implements PaymentMethodDetails {

    @Override
    @JsonIgnore
    public PaymentMethodType methodType() {
        return PaymentMethodType.CASH;
    }
}
