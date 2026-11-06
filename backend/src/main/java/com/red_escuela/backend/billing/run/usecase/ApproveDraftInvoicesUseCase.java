package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.DraftInvoiceBatchResponse;

import java.util.Collection;

public interface ApproveDraftInvoicesUseCase {
    DraftInvoiceBatchResponse approveDraftInvoicesOfRun(Long monthlyRunId, Collection<Long> invoiceIds, String idempotencyKey);
}
