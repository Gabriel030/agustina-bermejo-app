package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentVersion;

import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

public record LatePaymentTimeline(NavigableMap<LocalDate, LatePaymentVersion> versionsByEffectiveFrom) {

    public LatePaymentTimeline {
        Objects.requireNonNull(versionsByEffectiveFrom, "versionsByEffectiveFrom");
    }

    public LatePaymentTerms termsOn(LocalDate day, LatePaymentTerms fallback) {
        Map.Entry<LocalDate, LatePaymentVersion> entry = versionsByEffectiveFrom.floorEntry(day);
        if (entry != null) {
            return entry.getValue().terms();
        }
        return versionsByEffectiveFrom.isEmpty() ? fallback : versionsByEffectiveFrom.firstEntry().getValue().terms();
    }
}
