package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsView;

import java.time.LocalDateTime;

public record AuditedAnnualUpfrontTermsResponse(Integer versionNumber, String version, String text, Long publishedByPersonId, LocalDateTime publishedAt, boolean current) {

    public static AuditedAnnualUpfrontTermsResponse from(AnnualUpfrontTermsView view) {
        return new AuditedAnnualUpfrontTermsResponse(view.versionNumber(), view.version(), view.text(), view.publishedByPersonId(), view.publishedAt(), view.current());
    }
}
