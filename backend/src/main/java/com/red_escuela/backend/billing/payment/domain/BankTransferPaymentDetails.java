package com.red_escuela.backend.billing.payment.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

public record BankTransferPaymentDetails(String bankName, String originAccount, String operationNumber, String holderName,
                                         @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate transferDate) implements PaymentMethodDetails {

    @Override
    @JsonIgnore
    public PaymentMethodType methodType() {
        return PaymentMethodType.BANK_TRANSFER;
    }
}
