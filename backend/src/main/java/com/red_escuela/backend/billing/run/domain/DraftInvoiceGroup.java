package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.SignedPercentage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DraftInvoiceGroup(DraftInvoiceGroupKey key, int draftCount, Money draftTotal, Money previousIssuedTotal, List<Long> invoiceIds) {

    public DraftInvoiceGroup {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(draftTotal, "draftTotal");
        invoiceIds = List.copyOf(Objects.requireNonNull(invoiceIds, "invoiceIds"));
    }

    public Optional<Money> findPreviousIssuedTotal() {
        return Optional.ofNullable(previousIssuedTotal);
    }

    public Optional<Money> findDifferenceFromPreviousPeriod() {
        return findPreviousIssuedTotal().map(previous -> draftTotal.minus(previous));
    }

    public Optional<SignedPercentage> findPercentageDifferenceFromPreviousPeriod() {
        return findPreviousIssuedTotal().flatMap(previous -> SignedPercentage.changeBetween(previous, draftTotal));
    }
}
