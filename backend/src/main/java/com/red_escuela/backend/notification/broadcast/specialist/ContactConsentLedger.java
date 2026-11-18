package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.identity.api.NewsletterPreferenceFinder;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentDecision;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.ConsentStatus;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.dto.ContactConsentEntry;
import com.red_escuela.backend.notification.broadcast.entity.ContactConsent;
import com.red_escuela.backend.notification.broadcast.repository.ContactConsentRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Component
public class ContactConsentLedger {

    private final ContactConsentRepository contactConsentRepository;
    private final NewsletterPreferenceFinder newsletterPreferenceFinder;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public ContactConsentLedger(ContactConsentRepository contactConsentRepository,
                                NewsletterPreferenceFinder newsletterPreferenceFinder,
                                AuditTrail auditTrail,
                                Clock clock) {
        this.contactConsentRepository = contactConsentRepository;
        this.newsletterPreferenceFinder = newsletterPreferenceFinder;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConsentDecision isAllowed(Long personId, ChannelType channel, BroadcastCategory category) {
        Optional<ContactConsent> record = contactConsentRepository.findByPersonIdAndChannelAndCategory(personId, channel, category);
        return decide(personId, channel, category, record.map(ContactConsent::getStatus), newsletterPreferenceFinder::isOptedIn);
    }

    @Transactional(readOnly = true)
    public Map<Long, ConsentDecision> isAllowedAll(Collection<Long> personIds, ChannelType channel, BroadcastCategory category) {
        Set<Long> distinctIds = new HashSet<>(personIds);
        Map<Long, ConsentStatus> statusByPerson = new HashMap<>();
        if (!distinctIds.isEmpty()) {
            contactConsentRepository.findAllByPersonIdInAndChannelAndCategory(distinctIds, channel, category)
                    .forEach(record -> statusByPerson.put(record.getPersonId(), record.getStatus()));
        }
        Set<Long> optedInIds = Set.of();
        if (channel == ChannelType.EMAIL && category != BroadcastCategory.INSTITUTIONAL) {
            Set<Long> withoutRecord = new HashSet<>(distinctIds);
            withoutRecord.removeAll(statusByPerson.keySet());
            if (!withoutRecord.isEmpty()) {
                optedInIds = newsletterPreferenceFinder.findOptedInPersonIds(withoutRecord);
            }
        }
        Predicate<Long> newsletterOptedIn = optedInIds::contains;
        Map<Long, ConsentDecision> decisions = new HashMap<>();
        distinctIds.forEach(personId -> decisions.put(personId,
                decide(personId, channel, category, Optional.ofNullable(statusByPerson.get(personId)), newsletterOptedIn)));
        return decisions;
    }

    @Transactional(readOnly = true)
    public boolean isGranted(Long personId, ConsentChannel channel, BroadcastCategory category) {
        return channel.storageChannels().stream()
                .allMatch(storageChannel -> isAllowed(personId, storageChannel, category).allowed());
    }

    @Transactional(readOnly = true)
    public Optional<Instant> lastRecordedAt(Long personId, ConsentChannel channel, BroadcastCategory category) {
        return channel.storageChannels().stream()
                .map(storageChannel -> contactConsentRepository.findByPersonIdAndChannelAndCategory(personId, storageChannel, category))
                .flatMap(Optional::stream)
                .map(ContactConsent::getRecordedAt)
                .max(Instant::compareTo);
    }

    @Transactional(readOnly = true)
    public List<ContactConsentEntry> listEntries(Long personId) {
        return Arrays.stream(ConsentChannel.values())
                .flatMap(channel -> Arrays.stream(BroadcastCategory.values())
                        .map(category -> entryFor(personId, channel, category)))
                .toList();
    }

    @Transactional
    public ContactConsentEntry recordConsentAndDescribe(Long personId, ConsentChannel channel, BroadcastCategory category,
                                                        boolean granted, ConsentSource source, String evidence) {
        recordConsent(personId, channel, category, granted, source, evidence);
        return entryFor(personId, channel, category);
    }

    @Transactional
    public void recordConsent(Long personId, ConsentChannel channel, BroadcastCategory category, boolean granted,
                              ConsentSource source, String evidence) {
        ConsentStatus newStatus = granted ? ConsentStatus.GRANTED : ConsentStatus.REVOKED;
        String boundedEvidence = boundedEvidence(evidence);
        Instant now = clock.instant();
        channel.storageChannels().forEach(storageChannel ->
                recordOne(personId, storageChannel, category, newStatus, source, boundedEvidence, now));
    }

    private ContactConsentEntry entryFor(Long personId, ConsentChannel channel, BroadcastCategory category) {
        Optional<Instant> recordedAt = lastRecordedAt(personId, channel, category);
        return new ContactConsentEntry(channel, category, isGranted(personId, channel, category),
                recordedAt.orElse(null), recordedAt.isPresent());
    }

    private void recordOne(Long personId, ChannelType storageChannel, BroadcastCategory category, ConsentStatus newStatus,
                           ConsentSource source, String evidence, Instant now) {
        Optional<ContactConsent> existing = contactConsentRepository.findByPersonIdAndChannelAndCategory(personId, storageChannel, category);
        Optional<ConsentStatus> previousStatus = existing.map(ContactConsent::getStatus);
        existing.ifPresentOrElse(
                record -> record.recordDecision(newStatus, source, evidence, now),
                () -> contactConsentRepository.save(ContactConsent.builder()
                        .personId(personId).channel(storageChannel).category(category)
                        .status(newStatus).source(source).evidence(evidence).recordedAt(now).build()));
        if (!previousStatus.map(newStatus::equals).orElse(false)) {
            auditTrail.record(new AuditRecord(
                    newStatus == ConsentStatus.GRANTED ? AuditAction.CONTACT_CONSENT_GRANTED : AuditAction.CONTACT_CONSENT_REVOKED,
                    AuditEntityType.PERSON, personId, previousStatus.map(Enum::name).orElse(null), newStatus.name(),
                    storageChannel + "/" + category + " via " + source));
        }
    }

    private ConsentDecision decide(Long personId, ChannelType channel, BroadcastCategory category, Optional<ConsentStatus> status,
                                   Predicate<Long> newsletterOptedIn) {
        if (category == BroadcastCategory.INSTITUTIONAL) {
            return status.filter(ConsentStatus.REVOKED::equals).isPresent()
                    ? ConsentDecision.denied(DenialReason.OPTED_OUT)
                    : ConsentDecision.permitted();
        }
        if (status.isPresent()) {
            return status.get() == ConsentStatus.GRANTED ? ConsentDecision.permitted() : ConsentDecision.denied(DenialReason.OPTED_OUT);
        }
        return channel == ChannelType.EMAIL && newsletterOptedIn.test(personId)
                ? ConsentDecision.permitted()
                : ConsentDecision.denied(DenialReason.NO_CONSENT);
    }

    private static String boundedEvidence(String evidence) {
        return evidence == null || evidence.length() <= ContactConsent.MAX_EVIDENCE_LENGTH
                ? evidence
                : evidence.substring(0, ContactConsent.MAX_EVIDENCE_LENGTH);
    }
}
