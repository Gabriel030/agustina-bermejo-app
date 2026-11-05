package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;

public sealed interface InvoiceIssuingPreview {

    record InvoiceWouldBeIssued(NonNegativeMoney total) implements InvoiceIssuingPreview {
    }

    record InvoiceWouldBeAlreadyIssued(Long invoiceId) implements InvoiceIssuingPreview {
    }

    record InvoiceWouldBeRejected(PolicyEvaluationResult evaluationResult) implements InvoiceIssuingPreview {
    }
}
