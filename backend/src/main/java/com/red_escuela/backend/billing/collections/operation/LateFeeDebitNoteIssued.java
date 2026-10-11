package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.LateFeeCharge;
import com.red_escuela.backend.billing.invoice.domain.Invoice;

public record LateFeeDebitNoteIssued(Invoice debitNote, LateFeeCharge charge) {
}
