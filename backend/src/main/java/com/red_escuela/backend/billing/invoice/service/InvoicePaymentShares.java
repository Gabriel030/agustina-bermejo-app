package com.red_escuela.backend.billing.invoice.service;

import java.util.List;
import java.util.Map;

public record InvoicePaymentShares(Map<Long, List<InvoicePaymentShare>> resolvedByInvoiceId) {
}
