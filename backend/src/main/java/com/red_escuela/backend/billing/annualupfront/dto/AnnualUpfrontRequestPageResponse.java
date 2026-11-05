package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestPage;

import java.util.List;

public record AnnualUpfrontRequestPageResponse(List<AnnualUpfrontRequestDetailResponse> items, int page, int size, long totalElements) {

    public static AnnualUpfrontRequestPageResponse from(AnnualUpfrontRequestPage page) {
        return new AnnualUpfrontRequestPageResponse(page.items().stream().map(AnnualUpfrontRequestDetailResponse::from).toList(), page.page(), page.size(), page.totalElements());
    }
}
