package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public record ReceiptSelectedInvoice(Long invoiceId, NonNegativeMoney outstandingAtSelection) {
}
