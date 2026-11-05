package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;
import java.util.Optional;

public record DiscountDefinitionCheck(DiscountDefinitionDraft draft, Currency offeringCurrency, Long cohortProgramId) {

    public DiscountDefinitionCheck {
        Objects.requireNonNull(draft, "draft");
    }

    public static DiscountDefinitionCheck withoutResolvedScope(DiscountDefinitionDraft draft) {
        return new DiscountDefinitionCheck(draft, null, null);
    }

    public Optional<Currency> findOfferingCurrency() {
        return Optional.ofNullable(offeringCurrency);
    }

    public Optional<Long> findCohortProgramId() {
        return Optional.ofNullable(cohortProgramId);
    }
}
