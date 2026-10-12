package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;

@FunctionalInterface
public interface EnrollmentFeeOnDemandIssuer {
    void issueEnrollmentFeeOnDemand(PlannedInvoice charge);
}
