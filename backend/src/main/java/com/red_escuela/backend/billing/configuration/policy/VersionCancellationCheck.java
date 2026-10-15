package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;

import java.time.LocalDate;

public record VersionCancellationCheck(ConfigurationVersionStatus status, LocalDate effectiveFrom, LocalDate today, String reason) {
}
