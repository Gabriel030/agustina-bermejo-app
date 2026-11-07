package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTermsVersion;

import java.time.LocalDateTime;

public record AnnualUpfrontTermsView(Integer versionNumber, String version, String text, Long publishedByPersonId, LocalDateTime publishedAt, boolean current) {

    public static AnnualUpfrontTermsView of(AnnualUpfrontTermsVersion stored, boolean current) {
        return new AnnualUpfrontTermsView(stored.getVersionNumber(), stored.versionLabel(), stored.getText(), stored.getPublishedByPersonId(), stored.getPublishedAt(), current);
    }
}
