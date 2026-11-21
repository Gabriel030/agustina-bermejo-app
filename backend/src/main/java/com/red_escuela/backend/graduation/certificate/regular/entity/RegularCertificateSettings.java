package com.red_escuela.backend.graduation.certificate.regular.entity;

import java.util.Objects;

public record RegularCertificateSettings(ValidityDays validityDays, ValidityDays exceptionValidityDays, boolean evidenceRequired) {

    public static final RegularCertificateSettings DEFAULT = RegularCertificateSettings.of(30, 7, false);

    public RegularCertificateSettings {
        Objects.requireNonNull(validityDays);
        Objects.requireNonNull(exceptionValidityDays);
    }

    public static RegularCertificateSettings of(int validityDays, int exceptionValidityDays, boolean evidenceRequired) {
        return new RegularCertificateSettings(new ValidityDays(validityDays), new ValidityDays(exceptionValidityDays), evidenceRequired);
    }
}
