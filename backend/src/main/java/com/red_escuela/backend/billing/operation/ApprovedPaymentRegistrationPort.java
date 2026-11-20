package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Money;

import java.util.Optional;
import java.util.UUID;

public interface ApprovedPaymentRegistrationPort {

    void lockMercadoPagoPaymentUntilTransactionEnds(String mercadoPagoPaymentId);

    boolean isMercadoPagoPaymentRegistered(String mercadoPagoPaymentId);

    Optional<MercadoPagoCheckoutMatch> findMercadoPagoCheckout(UUID publicReference);

    void recordMercadoPagoAmountMismatch(Long invoiceId, Money expectedAmount, Money paidAmount, String mercadoPagoPaymentId);

    void recordMercadoPagoExpiredCheckout(Long invoiceId, String mercadoPagoPaymentId);

    void recordMercadoPagoUnmatchedPayment(String mercadoPagoPaymentId, UUID publicReference);

    Long registerApprovedMercadoPagoPayment(Long reportedInvoiceId, Money amount, Integer installments, CardPaymentType cardType, String mercadoPagoPaymentId);

    Long registerApprovedBankTransferPayment(Long invoiceId, Long reviewerPersonId);
}
