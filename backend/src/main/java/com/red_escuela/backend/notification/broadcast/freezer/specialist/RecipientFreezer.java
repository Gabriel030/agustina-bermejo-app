package com.red_escuela.backend.notification.broadcast.freezer.specialist;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.identity.api.PersonAudienceFinder;
import com.red_escuela.backend.identity.api.PersonAudienceView;
import com.red_escuela.backend.notification.broadcast.audience.domain.AudienceRecipient;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.audience.specialist.RecipientEligibilityEvaluator;
import com.red_escuela.backend.notification.broadcast.audience.specialist.SegmentResolver;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RecipientFreezer {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final SegmentResolver segmentResolver;
    private final PersonAudienceFinder personAudienceFinder;
    private final RecipientEligibilityEvaluator eligibilityEvaluator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RecipientFreezer(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository,
                            SegmentResolver segmentResolver, PersonAudienceFinder personAudienceFinder,
                            RecipientEligibilityEvaluator eligibilityEvaluator, ObjectMapper objectMapper, Clock clock) {
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
        this.segmentResolver = segmentResolver;
        this.personAudienceFinder = personAudienceFinder;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public int freezeRecipients(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId).orElseThrow(() -> new BroadcastNotFoundException(broadcastId));
        Set<Long> alreadyFrozen = recipientRepository.findPersonIdsByBroadcastId(broadcastId);
        List<PersonAudienceView> people = personAudienceFinder.findByIds(segmentResolver.resolvePersonIds(readSpec(broadcast))).stream()
                .filter(person -> !alreadyFrozen.contains(person.personId()))
                .sorted(Comparator.comparing(PersonAudienceView::personId))
                .toList();
        List<BroadcastRecipient> rows = people.stream().map(person -> toRecipient(broadcast, person)).toList();
        recipientRepository.saveAll(rows);
        return rows.size();
    }

    private BroadcastRecipient toRecipient(Broadcast broadcast, PersonAudienceView person) {
        Optional<ExclusionReason> reason = eligibilityEvaluator.findExclusionReason(
                new AudienceRecipient(person.personId(), person.fullName(), person.email(), person.emailVerified(), person.phoneNumber()),
                broadcast.getChannel(), broadcast.getCategory());
        return BroadcastRecipient.builder()
                .broadcastId(broadcast.getId())
                .personId(person.personId())
                .status(reason.isPresent() ? BroadcastRecipientStatus.EXCLUDED : BroadcastRecipientStatus.PENDING)
                .exclusionReason(reason.orElse(null))
                .addressMasked(maskedAddress(person, broadcast.getChannel()))
                .updatedAt(clock.instant())
                .build();
    }

    private SegmentSpecRequest readSpec(Broadcast broadcast) {
        try {
            return objectMapper.readValue(broadcast.getSegmentSpec(), SegmentSpecRequest.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String maskedAddress(PersonAudienceView person, ChannelType channel) {
        if (channel == ChannelType.EMAIL) {
            String email = person.email();
            if (email == null) {
                return null;
            }
            int at = email.indexOf('@');
            return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
        }
        String phone = person.phoneNumber();
        if (phone == null) {
            return null;
        }
        return phone.length() <= 4 ? "****" : "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
