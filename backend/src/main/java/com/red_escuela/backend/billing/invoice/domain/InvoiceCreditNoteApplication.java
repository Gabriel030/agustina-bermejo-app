package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record InvoiceCreditNoteApplication(
        CreditNoteApplicationKind kind,
        NonNegativeMoney offsetAmount,
        NonNegativeMoney freeCreditAmount,
        List<InvoiceLineCredit> lineCredits) {

    public InvoiceCreditNoteApplication {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(offsetAmount, "offsetAmount");
        Objects.requireNonNull(freeCreditAmount, "freeCreditAmount");
        lineCredits = List.copyOf(lineCredits);
        Set<Integer> lineNumbers = new HashSet<>();
        for (InvoiceLineCredit lineCredit : lineCredits) {
            if (!lineNumbers.add(lineCredit.lineNumber())) {
                throw new IllegalArgumentException("Repeated line " + lineCredit.lineNumber());
            }
        }
        NonNegativeMoney total = offsetAmount.plus(freeCreditAmount);
        if (total.value().isZero()) {
            throw new IllegalArgumentException("A credit note application must credit something");
        }
        NonNegativeMoney lineTotal = lineCredits.stream()
                .map(InvoiceLineCredit::amount)
                .reduce(NonNegativeMoney.zero(offsetAmount.value().currency()), NonNegativeMoney::plus);
        if (!lineTotal.equals(total)) {
            throw new IllegalArgumentException("The line credits must add up to offset plus free credit");
        }
    }

    public NonNegativeMoney totalCreditAmount() {
        return offsetAmount.plus(freeCreditAmount);
    }
}
