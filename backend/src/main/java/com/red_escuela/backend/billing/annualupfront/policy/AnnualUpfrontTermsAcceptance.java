package com.red_escuela.backend.billing.annualupfront.policy;

public record AnnualUpfrontTermsAcceptance(boolean termsAccepted, String acceptedVersion, boolean acceptedVersionWasPublished, String currentVersion) {

    public boolean acceptsAPublishedVersion() {
        return termsAccepted && acceptedVersionWasPublished;
    }

    public boolean acceptsTheCurrentVersion() {
        return currentVersion.equals(acceptedVersion);
    }
}
