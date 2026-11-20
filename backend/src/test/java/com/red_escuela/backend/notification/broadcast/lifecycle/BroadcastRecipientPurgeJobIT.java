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
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastRecipientPurgeJob;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BroadcastRecipientPurgeJobIT extends AbstractIntegrationTest {

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

    private final List<Long> createdBroadcastIds = new ArrayList<>();

    @AfterEach
    void cleanCreatedRows() {
        for (Long broadcastId : createdBroadcastIds) {
            jdbc.update("delete from audit_log where entity_type = 'BROADCAST' and entity_id = ?", broadcastId);
            jdbc.update("delete from broadcast_recipient where broadcast_id = ?", broadcastId);
            jdbc.update("delete from broadcast where id = ?", broadcastId);
        }
        createdBroadcastIds.clear();
    }

    private BroadcastRecipientPurgeJob job() {
        return job(100, 100);
    }

    private BroadcastRecipientPurgeJob job(int batchSize, int maxBatches) {
        return new BroadcastRecipientPurgeJob(recipientRepository, auditTrail,
                new RetentionSettings(true, 12, 12, 12, 36, batchSize, maxBatches), CLOCK, transactionManager, 12);
    }

    private Person person() {
        String unique = "purge" + System.nanoTime();
        return transactionTemplate.execute(status -> AudienceFreezeFixtures.person(entityManager, roleRepository, unique, true, "PRECEPTOR"));
    }

    private Broadcast broadcast(Person creator, BroadcastStatus status, Instant cancelledAt, Instant completedAt) {
        return broadcast(creator, status, cancelledAt, completedAt, true);
    }

    private Broadcast broadcast(Person creator, BroadcastStatus status, Instant cancelledAt, Instant completedAt, boolean frozenTotals) {
        Broadcast.BroadcastBuilder builder = Broadcast.builder().name("Aviso").category(BroadcastCategory.INSTITUTIONAL)
                .channel(ChannelType.EMAIL).status(status).segmentSpec(SPEC).subjectSnapshot("Asunto").bodySnapshot("Cuerpo")
                .createdByPersonId(creator.getId()).confirmedByPersonId(creator.getId()).confirmedAt(NOW.minusMonths(20).toInstant())
                .cancelledAt(cancelledAt).completedAt(completedAt).createdAt(NOW.minusMonths(20).toInstant());
        if (frozenTotals) {
            builder.sentTotal(1).failedTotal(0).excludedTotal(1).discardedTotal(0).excludedByReason("{\"OPTED_OUT\":1}");
        }
        Broadcast saved = broadcastRepository.saveAndFlush(builder.build());
        createdBroadcastIds.add(saved.getId());
        return saved;
    }

    private void recipients(Broadcast broadcast, Person sent, Person excluded) {
        Instant at = NOW.minusMonths(14).toInstant();
        recipientRepository.saveAndFlush(BroadcastRecipient.builder().broadcastId(broadcast.getId()).personId(sent.getId())
                .status(BroadcastRecipientStatus.SENT).addressMasked("a***@mail.com").updatedAt(at).build());
        recipientRepository.saveAndFlush(BroadcastRecipient.builder().broadcastId(broadcast.getId()).personId(excluded.getId())
                .status(BroadcastRecipientStatus.EXCLUDED).exclusionReason(ExclusionReason.OPTED_OUT).updatedAt(at).build());
    }

    private long auditEntries(Broadcast broadcast) {
        return jdbc.queryForObject("select count(*) from audit_log where entity_type = 'BROADCAST' and entity_id = ? and action = 'BROADCAST_RECIPIENTS_PURGED'",
                Long.class, broadcast.getId());
    }

    @Test
    void purgesAfterTwelveMonthsKeepingTotals() {
        Person creator = person();
        Broadcast cancelled = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(13).toInstant(), null);
        Broadcast completed = broadcast(creator, BroadcastStatus.COMPLETED, null, NOW.minusMonths(13).toInstant());
        recipients(cancelled, person(), person());
        recipients(completed, person(), person());

        job().purgeExpiredRecipients();

        for (Broadcast broadcast : new Broadcast[]{cancelled, completed}) {
            assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isZero();
            assertThat(auditEntries(broadcast)).isEqualTo(1);
            Broadcast reloaded = broadcastRepository.findById(broadcast.getId()).orElseThrow();
            assertThat(reloaded.getSentTotal()).isEqualTo(1);
            assertThat(reloaded.getExcludedTotal()).isEqualTo(1);
            assertThat(reloaded.getFailedTotal()).isZero();
            assertThat(reloaded.getDiscardedTotal()).isZero();
            assertThat(reloaded.getExcludedByReason()).contains("OPTED_OUT");
            assertThat(reloaded.getStatus()).isEqualTo(broadcast.getStatus());
            assertThat(reloaded.getCreatedByPersonId()).isEqualTo(creator.getId());
        }
    }

    @Test
    void exactCutoffDoesNotPurgeAndOneDayPastDoes() {
        Person creator = person();
        Broadcast exactCancelled = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(12).toInstant(), null);
        Broadcast exactCompleted = broadcast(creator, BroadcastStatus.COMPLETED, null, NOW.minusMonths(12).toInstant());
        Broadcast dayPast = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(12).minusDays(1).toInstant(), null);
        for (Broadcast broadcast : new Broadcast[]{exactCancelled, exactCompleted, dayPast}) {
            recipients(broadcast, person(), person());
        }

        job().purgeExpiredRecipients();

        assertThat(recipientRepository.countByBroadcastId(exactCancelled.getId())).isEqualTo(2);
        assertThat(recipientRepository.countByBroadcastId(exactCompleted.getId())).isEqualTo(2);
        assertThat(recipientRepository.countByBroadcastId(dayPast.getId())).isZero();
        assertThat(auditEntries(exactCancelled)).isZero();
        assertThat(auditEntries(dayPast)).isEqualTo(1);
    }

    @Test
    void openBroadcastsAreNeverPurged() {
        Person creator = person();
        Instant old = NOW.minusMonths(20).toInstant();
        Broadcast draft = broadcast(creator, BroadcastStatus.DRAFT, old, old);
        Broadcast confirmed = broadcast(creator, BroadcastStatus.CONFIRMED, old, old);
        Broadcast sending = broadcast(creator, BroadcastStatus.SENDING, old, old);
        Broadcast completedWithoutDate = broadcast(creator, BroadcastStatus.COMPLETED, null, null);
        Broadcast recent = broadcast(creator, BroadcastStatus.COMPLETED, null, NOW.minusMonths(11).toInstant());
        Broadcast[] untouched = {draft, confirmed, sending, completedWithoutDate, recent};
        for (Broadcast broadcast : untouched) {
            recipients(broadcast, person(), person());
        }

        job().purgeExpiredRecipients();

        for (Broadcast broadcast : untouched) {
            assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isEqualTo(2);
            assertThat(auditEntries(broadcast)).isZero();
        }
    }

    @Test
    void broadcastWithoutFrozenTotalsIsNeverPurged() {
        Broadcast withoutTotals = broadcast(person(), BroadcastStatus.COMPLETED, null, NOW.minusMonths(13).toInstant(), false);
        recipients(withoutTotals, person(), person());

        job().purgeExpiredRecipients();

        assertThat(recipientRepository.countByBroadcastId(withoutTotals.getId())).isEqualTo(2);
        assertThat(auditEntries(withoutTotals)).isZero();
    }

    @Test
    void isIdempotent() {
        Broadcast broadcast = broadcast(person(), BroadcastStatus.CANCELLED, NOW.minusMonths(13).toInstant(), null);
        recipients(broadcast, person(), person());

        job().purgeExpiredRecipients();
        int secondRun = job().purgeExpiredRecipients();

        assertThat(secondRun).isZero();
        assertThat(auditEntries(broadcast)).isEqualTo(1);
    }

    @Test
    void purgesInBatches() {
        Person creator = person();
        List<Broadcast> expired = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Broadcast broadcast = broadcast(creator, BroadcastStatus.CANCELLED, NOW.minusMonths(13).toInstant(), null);
            recipients(broadcast, person(), person());
            expired.add(broadcast);
        }

        int firstRun = job(2, 1).purgeExpiredRecipients();
        long purgedAfterFirstRun = expired.stream().filter(broadcast -> recipientRepository.countByBroadcastId(broadcast.getId()) == 0).count();
        job(2, 100).purgeExpiredRecipients();

        assertThat(firstRun).isEqualTo(2);
        assertThat(purgedAfterFirstRun).isEqualTo(2);
        expired.forEach(broadcast -> assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isZero());
    }
}
