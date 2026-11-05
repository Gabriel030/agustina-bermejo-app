package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptReviewDetail(Money declaredAmount, LocalDate transferDate, String operationNumber, String originAccount, String holderName,
                                  List<ReceiptInvoiceSummary> invoices, Integer pendingBusinessDays, LocalDateTime reviewAlertSentAt) {
}
