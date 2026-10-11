package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;

public record LateFeeDebitNoteDraftFacts(Long studentCurrentAccountId, Currency currency, StudentSnapshot recipient) {

    public LateFeeDebitNoteDraftFacts {
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(recipient, "recipient");
    }

    public static LateFeeDebitNoteDraftFacts addressedLikeOrigin(LateFeeOriginFacts origin) {
        return new LateFeeDebitNoteDraftFacts(origin.studentCurrentAccountId(), origin.currency(), origin.recipient());
    }
}
