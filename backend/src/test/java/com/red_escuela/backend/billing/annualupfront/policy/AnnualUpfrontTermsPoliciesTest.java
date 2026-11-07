package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-236")
class AnnualUpfrontTermsPoliciesTest {
    private final AnnualUpfrontTermsAcceptedPolicy acceptedPolicy = new AnnualUpfrontTermsAcceptedPolicy();
    private final AnnualUpfrontTermsCurrentPolicy currentPolicy = new AnnualUpfrontTermsCurrentPolicy();

    @Test
    void acceptingAPublishedVersionPassesTheAcceptedPolicy() {
        assertThat(acceptedPolicy.evaluateRule(new AnnualUpfrontTermsAcceptance(true, "v1", true, "v2")).allowed()).isTrue();
    }

    @Test
    void notCheckingTheBoxOrSendingAnUnpublishedVersionFailsTheAcceptedPolicy() {
        PolicyVerdict withoutBox = acceptedPolicy.evaluateRule(new AnnualUpfrontTermsAcceptance(false, "v1", true, "v1"));
        PolicyVerdict unknownVersion = acceptedPolicy.evaluateRule(new AnnualUpfrontTermsAcceptance(true, "v9", false, "v1"));

        assertThat(withoutBox.allowed()).isFalse();
        assertThat(withoutBox.code()).isEqualTo("ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED");
        assertThat(unknownVersion.allowed()).isFalse();
    }

    @Test
    void onlyTheCurrentVersionPassesTheCurrentPolicy() {
        assertThat(currentPolicy.evaluateRule(new AnnualUpfrontTermsAcceptance(true, "v2", true, "v2")).allowed()).isTrue();

        PolicyVerdict outdated = currentPolicy.evaluateRule(new AnnualUpfrontTermsAcceptance(true, "v1", true, "v2"));

        assertThat(outdated.allowed()).isFalse();
        assertThat(outdated.code()).isEqualTo("ANNUAL_UPFRONT_TERMS_OUTDATED");
    }
}
