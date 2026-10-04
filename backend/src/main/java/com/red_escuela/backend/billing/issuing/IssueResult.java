package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluation;

public sealed interface IssueResult {
    record Issued(Invoice invoice) implements IssueResult {
    }

    record AlreadyIssued(Long invoiceId) implements IssueResult {
    }

    record Rejected(PolicyEvaluation evaluation) implements IssueResult {
    }
}
