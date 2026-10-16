package com.red_escuela.backend.billing.note.domain;

import java.util.List;
import java.util.Objects;

public record WithdrawalCancellationItem(Long invoiceId, WithdrawalCancellationKind kind, List<Integer> lineNumbers) {

    public WithdrawalCancellationItem {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(kind, "kind");
        lineNumbers = lineNumbers == null ? List.of() : List.copyOf(lineNumbers);
        if (kind == WithdrawalCancellationKind.REDUCE_LINES && lineNumbers.isEmpty()) {
            throw new IllegalArgumentException("A line reduction needs at least one line");
        }
    }

    public static WithdrawalCancellationItem cancelling(Long invoiceId) {
        return new WithdrawalCancellationItem(invoiceId, WithdrawalCancellationKind.CANCEL_INVOICE, List.of());
    }

    public static WithdrawalCancellationItem reducing(Long invoiceId, List<Integer> lineNumbers) {
        return new WithdrawalCancellationItem(invoiceId, WithdrawalCancellationKind.REDUCE_LINES, lineNumbers);
    }
}
