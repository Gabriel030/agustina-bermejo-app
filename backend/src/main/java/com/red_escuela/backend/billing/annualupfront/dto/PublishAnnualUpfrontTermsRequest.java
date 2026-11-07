package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTermsVersion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PublishAnnualUpfrontTermsRequest(
        @NotNull @Size(min = AnnualUpfrontTermsVersion.MIN_TEXT_LENGTH, max = AnnualUpfrontTermsVersion.MAX_TEXT_LENGTH) String text) {
}
