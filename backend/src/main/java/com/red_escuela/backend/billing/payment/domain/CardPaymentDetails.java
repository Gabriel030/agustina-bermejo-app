package com.red_escuela.backend.billing.payment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CardPaymentDetails(CardPaymentType cardType, Integer installments, String lastFourDigits) implements PaymentMethodDetails {

    @Override
    @JsonIgnore
    public PaymentMethodType methodType() {
        return PaymentMethodType.CARD;
    }
}
