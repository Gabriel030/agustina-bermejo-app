package com.red_escuela.backend.notification.broadcast.audience.usecase;

import com.red_escuela.backend.identity.api.PersonAudienceFinder;
import com.red_escuela.backend.identity.api.PersonAudienceView;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.audience.domain.AudienceRecipient;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewResponse;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudienceSampleEntry;
import com.red_escuela.backend.notification.broadcast.audience.specialist.RecipientEligibilityEvaluator;
import com.red_escuela.backend.notification.broadcast.audience.specialist.SegmentResolver;
import com.red_escuela.backend.shared.UseCase;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@UseCase
public class PreviewBroadcastAudienceService implements PreviewBroadcastAudienceUseCase {

    private static final String INCLUDED = "INCLUDED";
    private static final String EXCLUDED = "EXCLUDED";

    private final SegmentResolver segmentResolver;
    private final PersonAudienceFinder personAudienceFinder;
    private final RecipientEligibilityEvaluator eligibilityEvaluator;

    public PreviewBroadcastAudienceService(SegmentResolver segmentResolver, PersonAudienceFinder personAudienceFinder,
                                           RecipientEligibilityEvaluator eligibilityEvaluator) {
        this.segmentResolver = segmentResolver;
        this.personAudienceFinder = personAudienceFinder;
        this.eligibilityEvaluator = eligibilityEvaluator;
    }

    @Override
    public AudiencePreviewResponse previewBroadcastAudience(AudiencePreviewRequest request) {
        List<PersonAudienceView> people = personAudienceFinder.findByIds(segmentResolver.resolvePersonIds(request.segmentSpec())).stream()
                .sorted(Comparator.comparing(PersonAudienceView::personId))
                .toList();
        Map<ExclusionReason, Integer> excludedByReason = new EnumMap<>(ExclusionReason.class);
        for (ExclusionReason reason : ExclusionReason.values()) {
            excludedByReason.put(reason, 0);
        }
        Map<Long, ExclusionReason> reasons = eligibilityEvaluator.findExclusionReasons(
                people.stream().map(person -> new AudienceRecipient(person.personId(), person.fullName(), person.email(),
                        person.emailVerified(), person.phoneNumber())).toList(),
                request.channel(), request.category());
        List<AudienceSampleEntry> evaluated = people.stream().map(person -> {
            Optional<ExclusionReason> reason = Optional.ofNullable(reasons.get(person.personId()));
            reason.ifPresent(found -> excludedByReason.merge(found, 1, Integer::sum));
            return new AudienceSampleEntry(person.personId(), person.fullName(), maskedAddress(person, request.channel()),
                    reason.isPresent() ? EXCLUDED : INCLUDED, reason.orElse(null));
        }).toList();
        int excluded = excludedByReason.values().stream().mapToInt(Integer::intValue).sum();
        int from = Math.min(request.page() * request.size(), evaluated.size());
        int to = Math.min(from + request.size(), evaluated.size());
        return new AudiencePreviewResponse(evaluated.size(), evaluated.size() - excluded, excludedByReason, evaluated.subList(from, to));
    }

    private static String maskedAddress(PersonAudienceView person, ChannelType channel) {
        if (channel == ChannelType.EMAIL) {
            return person.email() == null ? null : maskEmail(person.email());
        }
        return person.phoneNumber() == null ? null : maskPhone(person.phoneNumber());
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        return phone.length() <= 4 ? "****" : "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
