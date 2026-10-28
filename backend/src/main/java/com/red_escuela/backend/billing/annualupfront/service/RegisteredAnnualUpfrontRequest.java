package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.invoice.domain.Invoice;

public record RegisteredAnnualUpfrontRequest(AnnualUpfrontRequest request, Invoice invoice) {
}
