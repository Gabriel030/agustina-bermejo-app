package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;

public sealed interface InvoiceIssuingResult {
    record InvoiceIssued(Invoice invoice) implements InvoiceIssuingResult {
    }

    record InvoiceAlreadyIssued(Long invoiceId) implements InvoiceIssuingResult {
    }

    record InvoiceIssuingRejected(PolicyEvaluationResult evaluation) implements InvoiceIssuingResult {
    }
}
