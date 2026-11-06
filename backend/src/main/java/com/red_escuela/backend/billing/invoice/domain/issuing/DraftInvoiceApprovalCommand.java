package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.operation.IdempotencyKeyed;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.TreeSet;

public record DraftInvoiceApprovalCommand(Long monthlyInvoiceRunId, Collection<Long> invoiceIds, LocalDate approvalDate, String idempotencyKey,
                                          StatusChangeActor actor) implements IdempotencyKeyed {

    @Override
    public Object requestFingerprint() {
        return Map.of("monthlyInvoiceRunId", monthlyInvoiceRunId, "invoiceIds", new TreeSet<>(invoiceIds));
    }
}
