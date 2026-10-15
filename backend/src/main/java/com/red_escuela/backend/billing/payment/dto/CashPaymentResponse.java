package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CashPaymentResponse(Long paymentId, Money amount, LocalDate paidOn, LocalDateTime registeredAt, List<AllocatedInvoiceResponse> allocatedInvoices,
                                  List<AllocatedInvoiceResponse> allocatedFromRemainingCredit, Money unallocatedCredit) {

    public static CashPaymentResponse from(CashPaymentRegistrationResult result) {
        return new CashPaymentResponse(result.paymentId(), result.amount().value(), result.paidOn(), result.registeredAt(),
                result.allocatedInvoices().stream().map(invoice -> new AllocatedInvoiceResponse(invoice.invoiceId(), invoice.amount().value())).toList(),
                result.allocatedFromRemainingCredit().stream().map(invoice -> new AllocatedInvoiceResponse(invoice.invoiceId(), invoice.amount().value())).toList(),
                result.unallocatedCredit().value());
    }

    public record AllocatedInvoiceResponse(Long invoiceId, Money amount) {
    }
}
