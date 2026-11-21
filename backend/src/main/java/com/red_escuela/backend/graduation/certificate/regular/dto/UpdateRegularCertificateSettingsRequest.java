package com.red_escuela.backend.graduation.certificate.regular.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRegularCertificateSettingsRequest(@NotNull @Min(1) @Max(180) Integer validityDays,
                                                      @NotNull @Min(1) @Max(180) Integer exceptionValidityDays,
                                                      @NotNull Boolean evidenceRequired, @NotNull Long version) {
}
