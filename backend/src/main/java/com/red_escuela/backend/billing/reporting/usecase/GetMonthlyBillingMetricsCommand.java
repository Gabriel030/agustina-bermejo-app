package com.red_escuela.backend.billing.reporting.usecase;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;

import java.time.YearMonth;

public record GetMonthlyBillingMetricsCommand(String panel, YearMonth from, YearMonth to, Currency currency, Long programId, Long courseId,
                                              InvoiceType invoiceType) {
}
