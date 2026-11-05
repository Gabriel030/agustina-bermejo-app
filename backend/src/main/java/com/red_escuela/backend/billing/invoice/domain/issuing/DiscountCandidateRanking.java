package com.red_escuela.backend.billing.invoice.domain.issuing;

import java.util.Comparator;

public final class DiscountCandidateRanking implements Comparator<InvoiceDiscountCandidate> {

    private static final Comparator<InvoiceDiscountCandidate> BEST_FIRST = Comparator
            .comparing((InvoiceDiscountCandidate candidate) -> candidate.discountAmount().value().amount(), Comparator.reverseOrder())
            .thenComparingInt(InvoiceDiscountCandidate::typeRank)
            .thenComparing(InvoiceDiscountCandidate::priority, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(InvoiceDiscountCandidate::grantedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(InvoiceDiscountCandidate::origin)
            .thenComparing(InvoiceDiscountCandidate::studentDiscountId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(InvoiceDiscountCandidate::sourceId);

    @Override
    public int compare(InvoiceDiscountCandidate first, InvoiceDiscountCandidate second) {
        return BEST_FIRST.compare(first, second);
    }

    public boolean ranksAbove(InvoiceDiscountCandidate challenger, InvoiceDiscountCandidate incumbent) {
        return compare(challenger, incumbent) < 0;
    }
}
