package com.red_escuela.backend.notification.broadcast.draft;

import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.freezer.specialist.RecipientFreezer;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecipientFreezerIT extends AbstractIntegrationTest {

    private static final String SPEC = "{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"PRECEPTOR\"}]}";

    @Autowired
    private RecipientFreezer recipientFreezer;

    @Autowired
    private BroadcastRepository broadcastRepository;

    @Autowired
    private BroadcastRecipientRepository recipientRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ContactConsentLedger consentLedger;

    @Autowired
    private JdbcTemplate jdbc;

    private Broadcast createDraft(Long creatorId) {
        return createDraft(creatorId, BroadcastCategory.INSTITUTIONAL, ChannelType.EMAIL);
    }

    private Broadcast createDraft(Long creatorId, BroadcastCategory category, ChannelType channel) {
        return broadcastRepository.saveAndFlush(Broadcast.builder()
                .name("Test")
                .category(category)
                .channel(channel)
                .status(BroadcastStatus.DRAFT)
                .segmentSpec(SPEC)
                .subjectSnapshot("Asunto")
                .bodySnapshot("Cuerpo")
                .createdByPersonId(creatorId)
                .createdAt(Instant.now(MutableClock.INSTANCE))
                .build());
    }

    private BroadcastRecipient recipientOf(Long broadcastId, Long personId) {
        return recipientRepository.findAll().stream()
                .filter(row -> row.getBroadcastId().equals(broadcastId) && row.getPersonId().equals(personId))
                .findFirst().orElseThrow();
    }

    @Test
    void freezesEligiblePeopleAsPendingAndExcludedOnesWithReason() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-creator", true, "DIRECTOR");
        Person eligible = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-eligible", true, "PRECEPTOR");
        Person unverified = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-unverified", false, "PRECEPTOR");
        Broadcast broadcast = createDraft(creator.getId());

        recipientFreezer.freezeRecipients(broadcast.getId());

        BroadcastRecipient eligibleRow = recipientOf(broadcast.getId(), eligible.getId());
        assertThat(eligibleRow.getStatus()).isEqualTo(BroadcastRecipientStatus.PENDING);
        assertThat(eligibleRow.getExclusionReason()).isNull();
        assertThat(eligibleRow.getAddressMasked()).contains("***@");
        BroadcastRecipient excludedRow = recipientOf(broadcast.getId(), unverified.getId());
        assertThat(excludedRow.getStatus()).isEqualTo(BroadcastRecipientStatus.EXCLUDED);
        assertThat(excludedRow.getExclusionReason()).isEqualTo(ExclusionReason.NO_VERIFIED_EMAIL);
    }

    @Test
    void runningTwiceDoesNotDuplicateRows() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-twice-creator", true, "DIRECTOR");
        AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-twice-a", true, "PRECEPTOR");
        AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-twice-b", false, "PRECEPTOR");
        Broadcast broadcast = createDraft(creator.getId());

        int first = recipientFreezer.freezeRecipients(broadcast.getId());
        entityManager.flush();
        long afterFirst = recipientRepository.countByBroadcastId(broadcast.getId());
        int second = recipientFreezer.freezeRecipients(broadcast.getId());
        entityManager.flush();

        assertThat(first).isEqualTo(2);
        assertThat(second).isZero();
        assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isEqualTo(afterFirst);
    }

    @Test
    void secondRunKeepsTheStatusOfAlreadyFrozenRows() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-keep-creator", true, "DIRECTOR");
        Person late = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-keep-a", true, "PRECEPTOR");
        Broadcast broadcast = createDraft(creator.getId());
        recipientFreezer.freezeRecipients(broadcast.getId());
        entityManager.flush();
        Person addedLater = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-keep-b", true, "PRECEPTOR");

        int added = recipientFreezer.freezeRecipients(broadcast.getId());
        entityManager.flush();

        List<Long> frozenIds = recipientRepository.findPersonIdsByBroadcastId(broadcast.getId()).stream().toList();
        assertThat(added).isEqualTo(1);
        assertThat(frozenIds).contains(late.getId(), addedLater.getId());
    }

    @Test
    void freezesOptedOutPersonWithReason() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-out-creator", true, "DIRECTOR");
        Person optedOut = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-out", true, "PRECEPTOR");
        consentLedger.recordConsent(optedOut.getId(), ConsentChannel.EMAIL, BroadcastCategory.INSTITUTIONAL, false, ConsentSource.PROFILE, null);
        Broadcast broadcast = createDraft(creator.getId());

        recipientFreezer.freezeRecipients(broadcast.getId());

        BroadcastRecipient row = recipientOf(broadcast.getId(), optedOut.getId());
        assertThat(row.getStatus()).isEqualTo(BroadcastRecipientStatus.EXCLUDED);
        assertThat(row.getExclusionReason()).isEqualTo(ExclusionReason.OPTED_OUT);
    }

    @Test
    void freezesPersonWithoutPromotionalConsentWithReason() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-noc-creator", true, "DIRECTOR");
        Person withoutConsent = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-noc", true, "PRECEPTOR");
        Broadcast broadcast = createDraft(creator.getId(), BroadcastCategory.PROMOTIONAL, ChannelType.EMAIL);

        recipientFreezer.freezeRecipients(broadcast.getId());

        BroadcastRecipient row = recipientOf(broadcast.getId(), withoutConsent.getId());
        assertThat(row.getStatus()).isEqualTo(BroadcastRecipientStatus.EXCLUDED);
        assertThat(row.getExclusionReason()).isEqualTo(ExclusionReason.NO_CONSENT);
    }

    @Test
    void freezesWhatsappRecipientsWithMaskedPhoneAndPhoneReasons() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-wa-creator", true, "DIRECTOR");
        Person valid = AudienceFreezeFixtures.personWithPhone(entityManager, roleRepository, "frz-wa-valid", true, "1155551234", "PRECEPTOR");
        Person invalid = AudienceFreezeFixtures.personWithPhone(entityManager, roleRepository, "frz-wa-invalid", true, "12345", "PRECEPTOR");
        Person withoutPhone = AudienceFreezeFixtures.personWithPhone(entityManager, roleRepository, "frz-wa-none", true, null, "PRECEPTOR");
        Broadcast broadcast = createDraft(creator.getId(), BroadcastCategory.INSTITUTIONAL, ChannelType.WHATSAPP_LINK);

        recipientFreezer.freezeRecipients(broadcast.getId());

        BroadcastRecipient validRow = recipientOf(broadcast.getId(), valid.getId());
        assertThat(validRow.getStatus()).isEqualTo(BroadcastRecipientStatus.PENDING);
        assertThat(validRow.getAddressMasked()).isEqualTo("******1234");
        BroadcastRecipient invalidRow = recipientOf(broadcast.getId(), invalid.getId());
        assertThat(invalidRow.getExclusionReason()).isEqualTo(ExclusionReason.NO_VALID_PHONE);
        assertThat(invalidRow.getAddressMasked()).isEqualTo("*2345");
        BroadcastRecipient noPhoneRow = recipientOf(broadcast.getId(), withoutPhone.getId());
        assertThat(noPhoneRow.getExclusionReason()).isEqualTo(ExclusionReason.NO_CHANNEL_ADDRESS);
        assertThat(noPhoneRow.getAddressMasked()).isNull();
    }

    @Test
    void duplicateRecipientIsRejectedByTheUniqueConstraint() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-uq-creator", true, "DIRECTOR");
        Broadcast broadcast = createDraft(creator.getId());
        String insert = "insert into broadcast_recipient (broadcast_id, person_id, status, updated_at) values (?, ?, 'PENDING', now())";
        jdbc.update(insert, broadcast.getId(), creator.getId());

        assertThatThrownBy(() -> jdbc.update(insert, broadcast.getId(), creator.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_broadcast_recipient");
    }

    @Test
    void excludedRecipientWithoutReasonIsRejectedByTheCheckConstraint() {
        Person creator = AudienceFreezeFixtures.person(entityManager, roleRepository, "frz-ck-creator", true, "DIRECTOR");
        Broadcast broadcast = createDraft(creator.getId());

        assertThatThrownBy(() -> jdbc.update("insert into broadcast_recipient (broadcast_id, person_id, status, updated_at) values (?, ?, 'EXCLUDED', now())",
                broadcast.getId(), creator.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_broadcast_recipient_reason");
    }
}
