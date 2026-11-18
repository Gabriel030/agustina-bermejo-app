package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.NewsletterPreferenceRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.AudienceRecipient;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.specialist.RecipientEligibilityEvaluator;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecipientEligibilityConsentIT extends AbstractIntegrationTest {

    private static final String INCLUDED = "INCLUDED";
    private static final String VALID_PHONE = "+5491155551234";

    @Autowired
    private RecipientEligibilityEvaluator evaluator;

    @Autowired
    private ContactConsentLedger ledger;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private NewsletterPreferenceRepository newsletterPreferenceRepository;

    private int sequence;

    private Person person(boolean emailVerified, String phone) {
        return AudienceTestPersons.create(entityManager, roleRepository, "elig-consent-" + (sequence++), emailVerified, phone);
    }

    private static AudienceRecipient recipientOf(Person person) {
        return new AudienceRecipient(person.getId(), person.getName(), person.getEmail().value(), Boolean.TRUE.equals(person.getEmailVerified()), person.getPhoneNumber());
    }

    private Map<String, String> batchVerdicts(ChannelType channel, BroadcastCategory category, Map<String, AudienceRecipient> recipients) {
        Map<Long, ExclusionReason> reasons = evaluator.findExclusionReasons(List.copyOf(recipients.values()), channel, category);
        Map<String, String> verdicts = new LinkedHashMap<>();
        recipients.forEach((label, recipient) -> verdicts.put(label,
                Optional.ofNullable(reasons.get(recipient.personId())).map(Enum::name).orElse(INCLUDED)));
        return verdicts;
    }

    private Map<String, String> expectedForEmail(BroadcastCategory category, Map<String, AudienceRecipient> recipients) {
        Map<String, String> verdicts = new LinkedHashMap<>();
        recipients.forEach((label, recipient) ->
                verdicts.put(label, evaluator.findExclusionReason(recipient, ChannelType.EMAIL, category).map(Enum::name).orElse(INCLUDED)));
        return verdicts;
    }

    private Map<String, AudienceRecipient> emailScenarios() {
        Map<String, AudienceRecipient> recipients = new LinkedHashMap<>();
        recipients.put("blankEmail", new AudienceRecipient(900001L, "Sin mail", " ", true, null));
        recipients.put("unverified", recipientOf(person(false, null)));
        Person revoked = person(true, null);
        ledger.recordConsent(revoked.getId(), ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, false, ConsentSource.PROFILE, null);
        recipients.put("revoked", recipientOf(revoked));
        Person granted = person(true, null);
        ledger.recordConsent(granted.getId(), ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, true, ConsentSource.PROFILE, null);
        recipients.put("granted", recipientOf(granted));
        recipients.put("noRecord", recipientOf(person(true, null)));
        Person newsletter = person(true, null);
        newsletterPreferenceRepository.upsert(newsletter.getId(), true, LocalDateTime.now());
        recipients.put("newsletterOptIn", recipientOf(newsletter));
        Person newsletterOptedOut = person(true, null);
        newsletterPreferenceRepository.upsert(newsletterOptedOut.getId(), false, LocalDateTime.now());
        recipients.put("newsletterOptOut", recipientOf(newsletterOptedOut));
        entityManager.flush();
        return recipients;
    }

    @Test
    void promotionalEmailVerdictsMatchEachExclusionReason() {
        Map<String, AudienceRecipient> recipients = emailScenarios();

        assertThat(expectedForEmail(BroadcastCategory.PROMOTIONAL, recipients)).containsExactly(
                Map.entry("blankEmail", ExclusionReason.NO_CHANNEL_ADDRESS.name()),
                Map.entry("unverified", ExclusionReason.NO_VERIFIED_EMAIL.name()),
                Map.entry("revoked", ExclusionReason.OPTED_OUT.name()),
                Map.entry("granted", INCLUDED),
                Map.entry("noRecord", ExclusionReason.NO_CONSENT.name()),
                Map.entry("newsletterOptIn", INCLUDED),
                Map.entry("newsletterOptOut", ExclusionReason.NO_CONSENT.name()));
        assertThat(batchVerdicts(ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL, recipients))
                .isEqualTo(expectedForEmail(BroadcastCategory.PROMOTIONAL, recipients));
    }

    @Test
    void institutionalEmailOnlyExcludesRevokedConsent() {
        Map<String, AudienceRecipient> recipients = emailScenarios();
        Person institutionalRevoked = person(true, null);
        ledger.recordConsent(institutionalRevoked.getId(), ConsentChannel.EMAIL, BroadcastCategory.INSTITUTIONAL, false, ConsentSource.PROFILE, null);
        recipients.put("institutionalRevoked", recipientOf(institutionalRevoked));

        assertThat(expectedForEmail(BroadcastCategory.INSTITUTIONAL, recipients)).containsExactly(
                Map.entry("blankEmail", ExclusionReason.NO_CHANNEL_ADDRESS.name()),
                Map.entry("unverified", ExclusionReason.NO_VERIFIED_EMAIL.name()),
                Map.entry("revoked", INCLUDED),
                Map.entry("granted", INCLUDED),
                Map.entry("noRecord", INCLUDED),
                Map.entry("newsletterOptIn", INCLUDED),
                Map.entry("newsletterOptOut", INCLUDED),
                Map.entry("institutionalRevoked", ExclusionReason.OPTED_OUT.name()));
        assertThat(batchVerdicts(ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL, recipients))
                .isEqualTo(expectedForEmail(BroadcastCategory.INSTITUTIONAL, recipients));
    }

    @Test
    void whatsappVerdictsMatchEachExclusionReason() {
        Person revoked = person(true, VALID_PHONE);
        ledger.recordConsent(revoked.getId(), ConsentChannel.WHATSAPP, BroadcastCategory.PROMOTIONAL, false, ConsentSource.PROFILE, null);
        Person granted = person(true, VALID_PHONE);
        ledger.recordConsent(granted.getId(), ConsentChannel.WHATSAPP, BroadcastCategory.PROMOTIONAL, true, ConsentSource.PROFILE, null);
        Person newsletter = person(true, VALID_PHONE);
        newsletterPreferenceRepository.upsert(newsletter.getId(), true, LocalDateTime.now());
        entityManager.flush();
        Map<String, AudienceRecipient> recipients = new LinkedHashMap<>();
        recipients.put("blankPhone", new AudienceRecipient(900002L, "Sin tel", "a@x.com", true, " "));
        recipients.put("invalidPhone", new AudienceRecipient(900003L, "Tel malo", "a@x.com", true, "123"));
        recipients.put("revoked", recipientOf(revoked));
        recipients.put("granted", recipientOf(granted));
        recipients.put("noRecord", recipientOf(person(true, VALID_PHONE)));
        recipients.put("newsletterDoesNotApplyToWhatsapp", recipientOf(newsletter));

        Map<String, String> verdicts = new LinkedHashMap<>();
        recipients.forEach((label, recipient) -> verdicts.put(label,
                evaluator.findExclusionReason(recipient, ChannelType.WHATSAPP_LINK, BroadcastCategory.PROMOTIONAL).map(Enum::name).orElse(INCLUDED)));

        assertThat(verdicts).containsExactly(
                Map.entry("blankPhone", ExclusionReason.NO_CHANNEL_ADDRESS.name()),
                Map.entry("invalidPhone", ExclusionReason.NO_VALID_PHONE.name()),
                Map.entry("revoked", ExclusionReason.OPTED_OUT.name()),
                Map.entry("granted", INCLUDED),
                Map.entry("noRecord", ExclusionReason.NO_CONSENT.name()),
                Map.entry("newsletterDoesNotApplyToWhatsapp", ExclusionReason.NO_CONSENT.name()));
        assertThat(batchVerdicts(ChannelType.WHATSAPP_LINK, BroadcastCategory.PROMOTIONAL, recipients)).isEqualTo(verdicts);
    }
}
