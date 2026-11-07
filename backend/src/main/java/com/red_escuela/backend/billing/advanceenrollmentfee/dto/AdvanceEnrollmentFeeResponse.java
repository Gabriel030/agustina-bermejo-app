package com.red_escuela.backend.billing.advanceenrollmentfee.dto;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;

public record AdvanceEnrollmentFeeResponse(Long invoiceId, boolean created) {

    public static AdvanceEnrollmentFeeResponse from(AdvanceEnrollmentFeeIssuance issuance) {
        return new AdvanceEnrollmentFeeResponse(issuance.invoiceId(), issuance.created());
    }
}
