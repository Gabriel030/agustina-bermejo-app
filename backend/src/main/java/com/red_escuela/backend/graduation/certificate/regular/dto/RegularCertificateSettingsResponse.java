package com.red_escuela.backend.graduation.certificate.regular.dto;

public record RegularCertificateSettingsResponse(int validityDays, int exceptionValidityDays, boolean evidenceRequired, long version) {
}
