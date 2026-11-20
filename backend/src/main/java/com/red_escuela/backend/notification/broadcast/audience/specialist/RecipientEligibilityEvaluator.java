package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentDecision;
import com.red_escuela.backend.notification.broadcast.domain.PhoneNumberNormalizer;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.audience.domain.AudienceRecipient;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RecipientEligibilityEvaluator {

    private final ContactConsentLedger consentLedger;

    public RecipientEligibilityEvaluator(ContactConsentLedger consentLedger) {
        this.consentLedger = consentLedger;
    }

    public Optional<ExclusionReason> findExclusionReason(AudienceRecipient recipient, ChannelType channel, BroadcastCategory category) {
        Optional<ExclusionReason> addressReason = findAddressExclusionReason(recipient, channel);
        if (addressReason.isPresent()) {
            return addressReason;
        }
        return consentExclusionReason(consentLedger.isAllowed(recipient.personId(), channel, category));
    }

    public Map<Long, ExclusionReason> findExclusionReasons(List<AudienceRecipient> recipients, ChannelType channel, BroadcastCategory category) {
        Map<Long, ExclusionReason> reasons = new HashMap<>();
        List<Long> withAddress = new ArrayList<>();
        for (AudienceRecipient recipient : recipients) {
            Optional<ExclusionReason> addressReason = findAddressExclusionReason(recipient, channel);
            if (addressReason.isPresent()) {
                reasons.put(recipient.personId(), addressReason.get());
            } else {
                withAddress.add(recipient.personId());
            }
        }
        consentLedger.isAllowedAll(withAddress, channel, category).forEach((personId, decision) ->
                consentExclusionReason(decision).ifPresent(reason -> reasons.put(personId, reason)));
        return reasons;
    }

    private static Optional<ExclusionReason> findAddressExclusionReason(AudienceRecipient recipient, ChannelType channel) {
        if (channel == ChannelType.EMAIL) {
            if (recipient.email() == null || recipient.email().isBlank()) {
                return Optional.of(ExclusionReason.NO_CHANNEL_ADDRESS);
            }
            if (!recipient.emailVerified()) {
                return Optional.of(ExclusionReason.NO_VERIFIED_EMAIL);
            }
        } else {
            if (recipient.phoneNumber() == null || recipient.phoneNumber().isBlank()) {
                return Optional.of(ExclusionReason.NO_CHANNEL_ADDRESS);
            }
            if (PhoneNumberNormalizer.normalizeArgentineMobile(recipient.phoneNumber()).isEmpty()) {
                return Optional.of(ExclusionReason.NO_VALID_PHONE);
            }
        }
        return Optional.empty();
    }

    private static Optional<ExclusionReason> consentExclusionReason(ConsentDecision decision) {
        if (decision.allowed()) {
            return Optional.empty();
        }
        return Optional.of(switch (decision.denialReason()) {
            case OPTED_OUT -> ExclusionReason.OPTED_OUT;
            case NO_CONSENT -> ExclusionReason.NO_CONSENT;
        });
    }
}
