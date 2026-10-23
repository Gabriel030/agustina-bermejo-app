package com.red_escuela.backend.billing.configuration.domain;

import java.util.Objects;

public record CohortEntryVersion(Long versionId, CohortEntryDiscount terms) {

    public CohortEntryVersion {
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(terms, "terms");
    }
}
