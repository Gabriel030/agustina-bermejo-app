package com.red_escuela.backend.notification.broadcast.draft;

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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private Broadcast createDraft(Long creatorId) {
        return broadcastRepository.saveAndFlush(Broadcast.builder()
                .name("Test")
                .category(BroadcastCategory.INSTITUTIONAL)
                .channel(ChannelType.EMAIL)
                .status(BroadcastStatus.DRAFT)
                .segmentSpec(SPEC)
                .subjectSnapshot("Asunto")
                .bodySnapshot("Cuerpo")
                .createdByPersonId(creatorId)
                .createdAt(Instant.now())
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

        assertThat(first).isGreaterThanOrEqualTo(2);
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
}
