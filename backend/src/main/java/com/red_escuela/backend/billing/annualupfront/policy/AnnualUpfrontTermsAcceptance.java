package com.red_escuela.backend.billing.annualupfront.policy;

public record AnnualUpfrontTermsAcceptance(boolean termsAccepted, String acceptedVersion, String currentVersion) {

    public boolean acceptsTheCurrentVersion() {
        return termsAccepted && currentVersion.equals(acceptedVersion);
    }
}
