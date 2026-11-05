package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceProposalPoliciesTest {

    @Test
    @Tag("D-116")
    @Tag("D-211")
    void aSecondOpenProposalOnTheSamePriceIsDeniedAndAFirstOneIsAllowed() {
        SingleOpenPriceProposalPolicy policy = new SingleOpenPriceProposalPolicy();

        PolicyVerdict first = policy.evaluateRule(new PriceProposalSlot(5L, false));
        PolicyVerdict second = policy.evaluateRule(new PriceProposalSlot(5L, true));

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.code()).isEqualTo("PROPOSAL_ALREADY_OPEN");
    }

    @Test
    @Tag("D-114")
    void rejectingAPriceProposalNeedsANonBlankReason() {
        PriceRejectionReasonPolicy policy = new PriceRejectionReasonPolicy();

        assertThat(policy.evaluateRule(new PriceRejectionCheck("caro")).allowed()).isTrue();
        assertThat(policy.evaluateRule(new PriceRejectionCheck(null)).code()).isEqualTo("REJECTION_REASON_REQUIRED");
        assertThat(policy.evaluateRule(new PriceRejectionCheck("   ")).code()).isEqualTo("REJECTION_REASON_REQUIRED");
    }

    @Test
    @Tag("D-114")
    void theCommandAsksTheDirectorUntilTheStartDateUnlessTheDirectorIsTheRequester() {
        ChangePriceCommand command = new ChangePriceCommand(5L, null, null, java.time.LocalDate.of(2027, 9, 1), "aumento", 7L, false);

        assertThat(command.authorizationExpiresOn()).isEqualTo(java.time.LocalDate.of(2027, 9, 1));
        assertThat(command.authorizationReason()).contains("2027-09-01").contains("aumento");
        assertThat(command.resumedFromAuthorizationRequest(44L).authorizationRequestId()).isEqualTo(44L);
        assertThat(command.authorizationRequestId()).isNull();
    }
}
