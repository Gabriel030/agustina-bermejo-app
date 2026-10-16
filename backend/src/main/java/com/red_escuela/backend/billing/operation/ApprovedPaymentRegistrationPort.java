package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.shared.domain.Money;

public interface ApprovedPaymentRegistrationPort {

    void lockMercadoPagoPaymentUntilTransactionEnds(String mercadoPagoPaymentId);

    boolean isMercadoPagoPaymentRegistered(String mercadoPagoPaymentId);

    Long registerApprovedMercadoPagoPayment(Long reportedInvoiceId, Money amount, Integer installments, CardPaymentType cardType, String mercadoPagoPaymentId);

    Long registerApprovedBankTransferPayment(Long invoiceId, Long reviewerPersonId);
}
