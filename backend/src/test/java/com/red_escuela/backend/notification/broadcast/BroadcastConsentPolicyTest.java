package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentDecision;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastConsentPolicy;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastDeliveryContext;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastConsentPolicyTest {

    private final BroadcastConsentPolicy policy = new BroadcastConsentPolicy();

    private PolicyVerdict evaluate(BroadcastCategory category, ConsentDecision decision) {
        return policy.evaluateRule(new BroadcastDeliveryContext(1L, ChannelType.EMAIL, category, decision));
    }

    @Test
    void deniesInstitutionalWhenOptedOut() {
        PolicyVerdict verdict = evaluate(BroadcastCategory.INSTITUTIONAL, ConsentDecision.denied(DenialReason.OPTED_OUT));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("OPTED_OUT");
    }

    @Test
    void deniesPromotionalWithoutConsent() {
        PolicyVerdict verdict = evaluate(BroadcastCategory.PROMOTIONAL, ConsentDecision.denied(DenialReason.NO_CONSENT));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("NO_CONSENT");
    }

    @Test
    void allowsWhenConsentIsGranted() {
        assertThat(evaluate(BroadcastCategory.PROMOTIONAL, ConsentDecision.permitted()).allowed()).isTrue();
    }
}
