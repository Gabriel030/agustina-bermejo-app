package com.red_escuela.backend.billing.payment.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CashPaymentDetails.class, name = "CASH"),
        @JsonSubTypes.Type(value = BankTransferPaymentDetails.class, name = "BANK_TRANSFER"),
        @JsonSubTypes.Type(value = CardPaymentDetails.class, name = "CARD"),
        @JsonSubTypes.Type(value = WalletPaymentDetails.class, name = "WALLET")})
public sealed interface PaymentMethodDetails permits CashPaymentDetails, BankTransferPaymentDetails, CardPaymentDetails, WalletPaymentDetails {

    PaymentMethodType methodType();
}
