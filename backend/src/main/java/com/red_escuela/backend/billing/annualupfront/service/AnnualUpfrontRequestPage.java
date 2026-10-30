package com.red_escuela.backend.billing.annualupfront.service;

import java.util.List;

public record AnnualUpfrontRequestPage(List<AnnualUpfrontRequestView> items, int page, int size, long totalElements) {

    public AnnualUpfrontRequestPage {
        items = List.copyOf(items);
    }
}
