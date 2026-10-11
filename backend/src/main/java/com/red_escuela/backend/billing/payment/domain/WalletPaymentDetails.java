package com.red_escuela.backend.billing.payment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record WalletPaymentDetails(String walletProvider, String payerAccount) implements PaymentMethodDetails {

    @Override
    @JsonIgnore
    public PaymentMethodType methodType() {
        return PaymentMethodType.WALLET;
    }
}
