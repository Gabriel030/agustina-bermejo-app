package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;

public sealed interface InvoiceIssuingResult {

    record InvoiceIssued(Invoice invoice) implements InvoiceIssuingResult {
    }

    record InvoiceAlreadyIssued(Long invoiceId) implements InvoiceIssuingResult {
    }

    record InvoiceIssuingRejected(PolicyEvaluationResult evaluationResult) implements InvoiceIssuingResult {
    }
}
