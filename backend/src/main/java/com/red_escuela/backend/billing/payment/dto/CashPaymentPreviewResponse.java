package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record CashPaymentPreviewResponse(List<CoveredInvoiceResponse> coveredInvoices, List<CoveredInvoiceResponse> coveredByRemainingCredit,
                                         Money unallocatedAfterwards, String previewFingerprint) {

    public static CashPaymentPreviewResponse from(CashPaymentPreview preview) {
        return new CashPaymentPreviewResponse(
                preview.coveredInvoices().stream()
                        .map(invoice -> new CoveredInvoiceResponse(invoice.invoiceId(), invoice.concept(), invoice.dueDate(), invoice.amount().value())).toList(),
                preview.coveredByRemainingCredit().stream()
                        .map(invoice -> new CoveredInvoiceResponse(invoice.invoiceId(), invoice.concept(), invoice.dueDate(), invoice.amount().value())).toList(),
                preview.unallocatedAfterwards().value(), preview.fingerprint());
    }

    public record CoveredInvoiceResponse(Long invoiceId, String concept, LocalDate dueDate, Money amount) {
    }
}
