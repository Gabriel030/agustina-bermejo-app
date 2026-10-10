package com.red_escuela.backend.billing.configuration.domain;

import java.util.Objects;

public record LatePaymentVersion(Long versionId, LatePaymentTerms terms) {

    public LatePaymentVersion {
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(terms, "terms");
    }
}
