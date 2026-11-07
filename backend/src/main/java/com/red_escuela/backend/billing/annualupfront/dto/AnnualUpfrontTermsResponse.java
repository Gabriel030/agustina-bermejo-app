package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsView;

import java.time.LocalDateTime;

public record AnnualUpfrontTermsResponse(Integer versionNumber, String version, String text, LocalDateTime publishedAt, boolean current) {

    public static AnnualUpfrontTermsResponse from(AnnualUpfrontTermsView view) {
        return new AnnualUpfrontTermsResponse(view.versionNumber(), view.version(), view.text(), view.publishedAt(), view.current());
    }
}
