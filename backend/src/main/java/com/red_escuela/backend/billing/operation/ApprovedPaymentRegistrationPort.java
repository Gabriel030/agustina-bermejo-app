package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Money;

public interface ApprovedPaymentRegistrationPort {

    boolean isMercadoPagoPaymentRegistered(String mercadoPagoPaymentId);

    Long registerApprovedMercadoPagoPayment(Long reportedInvoiceId, Money amount, Integer installments, String cardType, String mercadoPagoPaymentId);

    Long registerApprovedBankTransferPayment(Long invoiceId, Long reviewerPersonId);
}
