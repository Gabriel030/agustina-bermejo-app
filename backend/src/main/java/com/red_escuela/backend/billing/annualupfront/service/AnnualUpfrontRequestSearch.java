package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus;

public record AnnualUpfrontRequestSearch(AnnualUpfrontRequestStatus status, Integer lectiveYear) {
}
