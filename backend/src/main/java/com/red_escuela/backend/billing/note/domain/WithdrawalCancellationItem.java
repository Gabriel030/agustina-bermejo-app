package com.red_escuela.backend.billing.note.domain;

import java.util.List;
import java.util.Objects;

public record WithdrawalCancellationItem(Long invoiceId, WithdrawalCancellationKind kind, List<WithdrawalLineAmount> lines) {

    public WithdrawalCancellationItem {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(kind, "kind");
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (kind == WithdrawalCancellationKind.REDUCE_LINES && lines.isEmpty()) {
            throw new IllegalArgumentException("A line reduction needs at least one line");
        }
    }

    public static WithdrawalCancellationItem cancelling(Long invoiceId) {
        return new WithdrawalCancellationItem(invoiceId, WithdrawalCancellationKind.CANCEL_INVOICE, List.of());
    }

    public static WithdrawalCancellationItem reducing(Long invoiceId, List<WithdrawalLineAmount> lines) {
        return new WithdrawalCancellationItem(invoiceId, WithdrawalCancellationKind.REDUCE_LINES, lines);
    }
}
