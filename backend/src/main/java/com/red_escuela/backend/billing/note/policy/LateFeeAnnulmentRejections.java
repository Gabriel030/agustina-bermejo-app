package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class LateFeeAnnulmentRejections {

    private LateFeeAnnulmentRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return new PendingBankTransferOnInvoiceException(violations);
    }
}
