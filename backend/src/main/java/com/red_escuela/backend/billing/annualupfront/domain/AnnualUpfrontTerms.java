package com.red_escuela.backend.billing.annualupfront.domain;

import java.util.Objects;

public record AnnualUpfrontTerms(String version, String text) {

    public static final AnnualUpfrontTerms CURRENT = new AnnualUpfrontTerms("v1", "Texto de la condición del pago anual (D-236) a redactar por Gabriel.");

    public AnnualUpfrontTerms {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(text, "text");
    }

    public boolean isAcceptedBy(String acceptedVersion) {
        return version.equals(acceptedVersion);
    }
}
