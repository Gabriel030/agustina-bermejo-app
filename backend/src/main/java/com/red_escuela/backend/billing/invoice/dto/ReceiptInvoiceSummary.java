package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.shared.domain.Money;

public record ReceiptInvoiceSummary(Long invoiceId, String period, String feeType, Money totalAmount, Money outstandingAtSubmission) {
}
