package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.identity.api.MinorGuardianEmailCollisionFinder;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentDecision;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.audience.domain.AudienceRecipient;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.specialist.RecipientEligibilityEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecipientEligibilityEvaluatorTest {

    private final ContactConsentLedger ledger = mock(ContactConsentLedger.class);
    private final MinorGuardianEmailCollisionFinder minorGuardianEmailCollisionFinder = mock(MinorGuardianEmailCollisionFinder.class);
    private final RecipientEligibilityEvaluator evaluator = new RecipientEligibilityEvaluator(ledger, minorGuardianEmailCollisionFinder);

    private static AudienceRecipient recipient(String email, boolean verified, String phone) {
        return new AudienceRecipient(1L, "Ana Pérez", email, verified, phone);
    }

    private void consent(ConsentDecision decision) {
        when(ledger.isAllowed(any(), any(), any())).thenReturn(decision);
    }

    @Test
    void emailWithoutAddressIsExcludedBeforeAnyOtherCheck() {
        assertThat(evaluator.findExclusionReason(recipient(null, false, null), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL))
                .contains(ExclusionReason.NO_CHANNEL_ADDRESS);
        verifyNoInteractions(ledger);
    }

    @Test
    void unverifiedEmailIsExcludedBeforeConsent() {
        assertThat(evaluator.findExclusionReason(recipient("a@x.com", false, null), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL))
                .contains(ExclusionReason.NO_VERIFIED_EMAIL);
        verifyNoInteractions(ledger);
    }

    @Test
    void phoneWithoutNumberOrWithInvalidNumberIsExcluded() {
        assertThat(evaluator.findExclusionReason(recipient("a@x.com", true, " "), ChannelType.WHATSAPP_LINK, BroadcastCategory.INSTITUTIONAL))
                .contains(ExclusionReason.NO_CHANNEL_ADDRESS);
        assertThat(evaluator.findExclusionReason(recipient("a@x.com", true, "123"), ChannelType.WHATSAPP_LINK, BroadcastCategory.INSTITUTIONAL))
                .contains(ExclusionReason.NO_VALID_PHONE);
        verifyNoInteractions(ledger);
    }

    @Test
    void deniedConsentMapsToItsDenialReason() {
        consent(ConsentDecision.denied(DenialReason.OPTED_OUT));
        assertThat(evaluator.findExclusionReason(recipient("a@x.com", true, null), ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL))
                .contains(ExclusionReason.OPTED_OUT);

        consent(ConsentDecision.denied(DenialReason.NO_CONSENT));
        assertThat(evaluator.findExclusionReason(recipient("a@x.com", true, null), ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL))
                .contains(ExclusionReason.NO_CONSENT);
    }

    @Test
    void verifiedAddressWithConsentIsIncluded() {
        consent(ConsentDecision.permitted());

        assertThat(evaluator.findExclusionReason(recipient("a@x.com", true, null), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL)).isEmpty();
        assertThat(evaluator.findExclusionReason(recipient(null, false, "+5491155551234"), ChannelType.WHATSAPP_LINK, BroadcastCategory.INSTITUTIONAL)).isEmpty();
    }

    @Test
    void minorSharingEmailWithGuardianIsExcludedBeforeConsentIsConsulted() {
        when(minorGuardianEmailCollisionFinder.findMinorPersonIdsWithEmailSharedWithGuardian(any())).thenReturn(Set.of(1L));

        assertThat(evaluator.findExclusionReason(recipient("a@x.com", true, null), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL))
                .contains(ExclusionReason.NO_CHANNEL_ADDRESS);
        verifyNoInteractions(ledger);
    }

    @Test
    void guardianEmailCollisionIsNotLookedUpForWhatsapp() {
        consent(ConsentDecision.permitted());

        assertThat(evaluator.findExclusionReason(recipient(null, false, "+5491155551234"), ChannelType.WHATSAPP_LINK, BroadcastCategory.INSTITUTIONAL)).isEmpty();
        verifyNoInteractions(minorGuardianEmailCollisionFinder);
    }
}
