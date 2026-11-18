package com.red_escuela.backend.notification.broadcast.lifecycle;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastRecipientDataRetentionJob;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BroadcastRecipientDataRetentionIT extends AbstractIntegrationTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2027, 6, 15, 3, 20, 0, 0, ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
    private static final String SPEC = "{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"PRECEPTOR\"}]}";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private BroadcastRepository broadcastRepository;
    @Autowired
    private BroadcastRecipientRepository recipientRepository;
    @Autowired
    private AuditTrail auditTrail;
    @Autowired
    private JdbcTemplate jdbc;

    private BroadcastRecipientDataRetentionJob job() {
        return new BroadcastRecipientDataRetentionJob(broadcastRepository, recipientRepository, auditTrail, CLOCK, transactionManager, 12);
    }

    private Person person() {
        String unique = "retention" + System.nanoTime();
        return transactionTemplate.execute(status -> AudienceFreezeFixtures.person(entityManager, roleRepository, unique, true, "PRECEPTOR"));
    }

    private Broadcast broadcast(Person creator, BroadcastStatus status, Instant cancelledAt, Instant completedAt) {
        return broadcastRepository.saveAndFlush(Broadcast.builder().name("Aviso").category(BroadcastCategory.INSTITUTIONAL)
                .channel(ChannelType.EMAIL).status(status).segmentSpec(SPEC).subjectSnapshot("Asunto").bodySnapshot("Cuerpo")
                .createdByPersonId(creator.getId()).confirmedByPersonId(creator.getId()).confirmedAt(NOW.minusMonths(20).toInstant())
                .cancelledAt(cancelledAt).completedAt(completedAt).createdAt(NOW.minusMonths(20).toInstant()).build());
    }

    private void recipients(Broadcast broadcast, Person sent, Person excluded) {
        Instant at = NOW.minusMonths(14).toInstant();
        recipientRepository.saveAndFlush(BroadcastRecipient.builder().broadcastId(broadcast.getId()).personId(sent.getId())
                .status(BroadcastRecipientStatus.SENT).addressMasked("a***@mail.com").updatedAt(at).build());
        recipientRepository.saveAndFlush(BroadcastRecipient.builder().broadcastId(broadcast.getId()).personId(excluded.getId())
                .status(BroadcastRecipientStatus.EXCLUDED).exclusionReason(ExclusionReason.OPTED_OUT).updatedAt(at).build());
    }

    private long auditEntries(Broadcast broadcast) {
        return jdbc.queryForObject("select count(*) from audit_log where entity_type = 'BROADCAST' and entity_id = ? and action = 'BROADCAST_RECIPIENT_DATA_ANONYMIZED'",
                Long.class, broadcast.getId());
    }

    private long withAddress(Broadcast broadcast) {
        return jdbc.queryForObject("select count(*) from broadcast_recipient where broadcast_id = ? and address_masked is not null",
                Long.class, broadcast.getId());
    }

    private long withReason(Broadcast broadcast, String reason) {
        return jdbc.queryForObject("select count(*) from broadcast_recipient where broadcast_id = ? and exclusion_reason = ?",
                Long.class, broadcast.getId(), reason);
    }

    @Test
    void retentionRemovesRecipientDataButKeepsAggregates() {
        Person creator = person();
        Broadcast cancelled = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(13).toInstant(), null);
        Broadcast completed = broadcast(creator, BroadcastStatus.COMPLETED, null, NOW.minusMonths(13).toInstant());
        recipients(cancelled, person(), person());
        recipients(completed, person(), person());

        job().anonymizeExpiredRecipientData();

        for (Broadcast broadcast : new Broadcast[]{cancelled, completed}) {
            assertThat(withAddress(broadcast)).isZero();
            assertThat(withReason(broadcast, "OPTED_OUT")).isEqualTo(1);
            assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isEqualTo(2);
            assertThat(recipientRepository.countByStatus(broadcast.getId())).extracting(count -> count.status().name() + ":" + count.total())
                    .containsExactlyInAnyOrder("SENT:1", "EXCLUDED:1");
            assertThat(auditEntries(broadcast)).isEqualTo(1);
            Broadcast reloaded = broadcastRepository.findById(broadcast.getId()).orElseThrow();
            assertThat(reloaded.getCreatedByPersonId()).isEqualTo(creator.getId());
            assertThat(reloaded.getConfirmedByPersonId()).isEqualTo(creator.getId());
            assertThat(reloaded.getConfirmedAt()).isNotNull();
            assertThat(reloaded.getStatus()).isEqualTo(broadcast.getStatus());
        }
    }

    @Test
    void retentionIsIdempotent() {
        Person creator = person();
        Broadcast broadcast = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(13).toInstant(), null);
        recipients(broadcast, person(), person());

        job().anonymizeExpiredRecipientData();
        int secondRun = job().anonymizeExpiredRecipientData();

        assertThat(secondRun).isZero();
        assertThat(auditEntries(broadcast)).isEqualTo(1);
        assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isEqualTo(2);
    }

    @Test
    void nothingBeforeTwelveMonths() {
        Person creator = person();
        Instant old = NOW.minusMonths(20).toInstant();
        Broadcast cancelledRecently = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(12).plusMinutes(1).toInstant(), null);
        Broadcast completedRecently = broadcast(creator, BroadcastStatus.COMPLETED, null, NOW.minusMonths(11).toInstant());
        Broadcast sendingWithOldDates = broadcast(creator, BroadcastStatus.SENDING, old, old);
        Broadcast confirmedWithOldDates = broadcast(creator, BroadcastStatus.CONFIRMED, old, old);
        Broadcast draftWithOldDates = broadcast(creator, BroadcastStatus.DRAFT, old, old);
        Broadcast completedWithoutDate = broadcast(creator, BroadcastStatus.COMPLETED, null, null);
        Broadcast[] untouched = {cancelledRecently, completedRecently, sendingWithOldDates, confirmedWithOldDates, draftWithOldDates, completedWithoutDate};
        for (Broadcast broadcast : untouched) {
            recipients(broadcast, person(), person());
        }
        Broadcast expired = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(12).minusMinutes(1).toInstant(), null);
        recipients(expired, person(), person());

        int anonymized = job().anonymizeExpiredRecipientData();

        assertThat(anonymized).isGreaterThanOrEqualTo(1);
        assertThat(withAddress(expired)).isZero();
        assertThat(auditEntries(expired)).isEqualTo(1);
        for (Broadcast broadcast : untouched) {
            assertThat(withAddress(broadcast)).isEqualTo(1);
            assertThat(withReason(broadcast, "OPTED_OUT")).isEqualTo(1);
            assertThat(auditEntries(broadcast)).isZero();
        }
    }
}
