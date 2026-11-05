package com.red_escuela.backend.billing.note.domain;

import java.util.List;

public record WithdrawalCancellationPlan(List<WithdrawalCancellationItem> requestItems, List<Long> automaticInvoiceIds, List<Long> draftInvoiceIds) {

    public WithdrawalCancellationPlan {
        requestItems = List.copyOf(requestItems);
        automaticInvoiceIds = List.copyOf(automaticInvoiceIds);
        draftInvoiceIds = List.copyOf(draftInvoiceIds);
    }

    public boolean isEmpty() {
        return requestItems.isEmpty() && automaticInvoiceIds.isEmpty() && draftInvoiceIds.isEmpty();
    }
}
