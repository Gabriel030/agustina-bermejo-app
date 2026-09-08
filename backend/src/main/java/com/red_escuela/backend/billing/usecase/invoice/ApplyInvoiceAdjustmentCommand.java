package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateInvoiceAdjustmentRequest;

public record ApplyInvoiceAdjustmentCommand(Long invoiceId, CreateInvoiceAdjustmentRequest request) {
}
