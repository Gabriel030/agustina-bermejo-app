package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastDetailResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastCanceller;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastConfirmer;
import com.red_escuela.backend.notification.broadcast.lifecycle.usecase.GetBroadcastUseCase;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastLifecycleEndToEndIT extends AbstractBroadcastDispatchIT {

    private static final String SPEC = "{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"PRECEPTOR\"}]}";
    private static final LocalDate FIRST_DAY = LocalDate.of(2041, 6, 10);

    @Autowired
    private BroadcastRepository broadcastRepository;
    @Autowired
    private BroadcastConfirmer confirmer;
    @Autowired
    private BroadcastCanceller canceller;
    @Autowired
    private GetBroadcastUseCase getBroadcast;

    @BeforeEach
    void startWithNoPreceptors() {
        jdbc.update("delete from person_role where role_id = (select id from role where name = 'PRECEPTOR')");
        at(FIRST_DAY, 9, 0);
    }

    @AfterEach
    void cleanBroadcasts() {
        jdbc.update("delete from broadcast_recipient");
        jdbc.update("delete from broadcast");
    }

    private Person preceptor() {
        Person person = persistPerson();
        jdbc.update("insert into person_role (person_id, role_id) select ?, id from role where name = 'PRECEPTOR'", person.getId());
        return person;
    }

    private Person eligiblePreceptor() {
        Person person = preceptor();
        grantConsent(person, BroadcastCategory.PROMOTIONAL);
        return person;
    }

    private Person optedOutPreceptor() {
        Person person = preceptor();
        grantConsent(person, BroadcastCategory.PROMOTIONAL);
        revokeConsent(person, BroadcastCategory.PROMOTIONAL);
        return person;
    }

    private Broadcast confirmedBroadcast(int eligible, boolean withExcluded) {
        Person creator = persistPerson();
        for (int i = 0; i < eligible; i++) {
            eligiblePreceptor();
        }
        if (withExcluded) {
            optedOutPreceptor();
            preceptor();
        }
        Broadcast broadcast = broadcastRepository.saveAndFlush(Broadcast.builder().name("Aviso").category(BroadcastCategory.PROMOTIONAL)
                .channel(ChannelType.EMAIL).status(BroadcastStatus.DRAFT).segmentSpec(SPEC).subjectSnapshot("Asunto")
                .bodySnapshot("Cuerpo").createdByPersonId(creator.getId()).createdAt(MutableClock.INSTANCE.instant()).build());
        confirmer.confirmBroadcast(broadcast.getId(), creator.getId());
        return broadcastRepository.findById(broadcast.getId()).orElseThrow();
    }

    private Broadcast reload(Broadcast broadcast) {
        return broadcastRepository.findById(broadcast.getId()).orElseThrow();
    }

    private long recipients(Broadcast broadcast, BroadcastRecipientStatus status) {
        return jdbc.queryForObject("select count(*) from broadcast_recipient where broadcast_id = ? and status = ?", Long.class,
                broadcast.getId(), status.name());
    }

    private long auditEntries(Broadcast broadcast, String action) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_type = 'BROADCAST' and entity_id = ?",
                Long.class, action, broadcast.getId());
    }

    private int drain(BroadcastEmailDispatchJob job) {
        int total = 0;
        int run;
        do {
            run = job.dispatchPendingBroadcastEmails();
            total += run;
        } while (run > 0);
        return total;
    }

    @Test
    void confirmedBroadcastGoesThroughSendingToCompletedWithFrozenTotalsAcrossTwoDays() {
        Broadcast broadcast = confirmedBroadcast(3, true);
        assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.CONFIRMED);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.QUEUED)).isEqualTo(3);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.EXCLUDED)).isEqualTo(2);
        BroadcastEmailDispatchJob job = jobWith(2, 0, 100, recordingChannel(realGateway));

        assertThat(drain(job)).isEqualTo(2);

        Broadcast afterFirstDay = reload(broadcast);
        assertThat(afterFirstDay.getStatus()).isEqualTo(BroadcastStatus.SENDING);
        assertThat(afterFirstDay.getCompletedAt()).isNull();
        assertThat(afterFirstDay.hasFrozenTotals()).isFalse();
        assertThat(recipients(broadcast, BroadcastRecipientStatus.SENT)).isEqualTo(2);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.QUEUED)).isEqualTo(1);
        assertThat(getBroadcast.getBroadcast(broadcast.getId()).recipientCounts().get(BroadcastRecipientStatus.SENT)).isEqualTo(2L);

        at(FIRST_DAY.plusDays(1), 9, 0);
        assertThat(drain(job)).isEqualTo(1);

        Broadcast completed = reload(broadcast);
        assertThat(completed.getStatus()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isEqualTo(MutableClock.INSTANCE.instant());
        assertThat(List.of(completed.getSentTotal(), completed.getFailedTotal(), completed.getExcludedTotal(), completed.getDiscardedTotal()))
                .containsExactly(3, 0, 2, 0);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.SENT)).isEqualTo(3);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.QUEUED)).isZero();
        assertThat(auditEntries(broadcast, "BROADCAST_COMPLETED")).isEqualTo(1);

        at(FIRST_DAY.plusDays(2), 9, 0);
        assertThat(drain(job)).isZero();
        job.dispatchPendingBroadcastEmails();
        Broadcast afterRerun = reload(broadcast);
        assertThat(afterRerun.getStatus()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(afterRerun.getCompletedAt()).isEqualTo(completed.getCompletedAt());
        assertThat(auditEntries(broadcast, "BROADCAST_COMPLETED")).isEqualTo(1);
        assertThat(sentSubjects).hasSize(3);
    }

    @Test
    void trackerIsIdempotentAndIgnoresDeliveriesThatBelongToNoBroadcast() {
        Broadcast broadcast = confirmedBroadcast(1, false);
        Long deliveryId = jdbc.queryForObject("select delivery_id from broadcast_recipient where broadcast_id = ? and status = 'QUEUED'",
                Long.class, broadcast.getId());
        BroadcastEmailDispatchJob job = jobWith(10, 0, 100, recordingChannel(realGateway));
        drain(job);
        Broadcast completed = reload(broadcast);

        progressTracker.syncDelivery(deliveryId);
        progressTracker.syncDelivery(deliveryId);
        progressTracker.syncDelivery(-1L);

        Broadcast after = reload(broadcast);
        assertThat(after.getStatus()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(after.getCompletedAt()).isEqualTo(completed.getCompletedAt());
        assertThat(auditEntries(broadcast, "BROADCAST_COMPLETED")).isEqualTo(1);
    }

    @Test
    void cancellingInTheMiddleFreezesTotalsAndNothingMoreGoesOut() {
        Broadcast broadcast = confirmedBroadcast(3, true);
        BroadcastEmailDispatchJob job = jobWith(2, 0, 100, recordingChannel(realGateway));
        drain(job);
        assertThat(reload(broadcast).getStatus()).isEqualTo(BroadcastStatus.SENDING);

        canceller.cancelBroadcast(broadcast.getId(), persistPerson().getId(), "Error en el texto");

        Broadcast cancelled = reload(broadcast);
        assertThat(cancelled.getStatus()).isEqualTo(BroadcastStatus.CANCELLED);
        assertThat(List.of(cancelled.getSentTotal(), cancelled.getFailedTotal(), cancelled.getExcludedTotal(), cancelled.getDiscardedTotal()))
                .containsExactly(2, 0, 2, 1);
        at(FIRST_DAY.plusDays(1), 9, 0);
        assertThat(drain(job)).isZero();
        Broadcast afterNextDay = reload(broadcast);
        assertThat(afterNextDay.getStatus()).isEqualTo(BroadcastStatus.CANCELLED);
        assertThat(afterNextDay.getCompletedAt()).isNull();
        assertThat(auditEntries(broadcast, "BROADCAST_COMPLETED")).isZero();
        assertThat(sentSubjects).hasSize(2);
    }

    @Test
    void readContractUsesFrozenTotalsOnceRecipientRowsAreGone() {
        Broadcast broadcast = confirmedBroadcast(3, true);
        drain(jobWith(10, 0, 100, recordingChannel(realGateway)));
        jdbc.update("update broadcast_recipient set delivery_id = null where broadcast_id = ?", broadcast.getId());
        jdbc.update("delete from broadcast_recipient where broadcast_id = ?", broadcast.getId());

        BroadcastDetailResponse detail = getBroadcast.getBroadcast(broadcast.getId());

        assertThat(detail.status()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(detail.recipientCounts()).containsEntry(BroadcastRecipientStatus.SENT, 3L)
                .containsEntry(BroadcastRecipientStatus.EXCLUDED, 2L)
                .containsEntry(BroadcastRecipientStatus.FAILED, 0L)
                .containsEntry(BroadcastRecipientStatus.SKIPPED, 0L);
    }

    @Test
    void suppressedByOptOutAndFailedDeliveriesUpdateTheirRecipientsAndCompleteTheBroadcast() {
        Person optsOut = eligiblePreceptor();
        Person fails = eligiblePreceptor();
        Person creator = persistPerson();
        Broadcast draft = broadcastRepository.saveAndFlush(Broadcast.builder().name("Aviso").category(BroadcastCategory.PROMOTIONAL)
                .channel(ChannelType.EMAIL).status(BroadcastStatus.DRAFT).segmentSpec(SPEC).subjectSnapshot("Asunto")
                .bodySnapshot("Cuerpo").createdByPersonId(creator.getId()).createdAt(MutableClock.INSTANCE.instant()).build());
        confirmer.confirmBroadcast(draft.getId(), creator.getId());
        Long failingDeliveryId = jdbc.queryForObject("""
                select delivery_id from broadcast_recipient where broadcast_id = ? and person_id = ?""", Long.class, draft.getId(), fails.getId());
        revokeConsent(optsOut, BroadcastCategory.PROMOTIONAL);

        transactionTemplate.executeWithoutResult(status -> deliveryRepository.findByIdForUpdate(failingDeliveryId).orElseThrow()
                .recordFailedAttempt(DeliveryStatusReason.PROVIDER_ERROR, 1));
        progressTracker.syncDelivery(failingDeliveryId);

        assertThat(reload(draft).getStatus()).isEqualTo(BroadcastStatus.SENDING);
        assertThat(recipients(draft, BroadcastRecipientStatus.FAILED)).isEqualTo(1);

        drain(jobWith(10, 0, 100, recordingChannel(realGateway)));

        Broadcast completed = reload(draft);
        assertThat(completed.getStatus()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(recipients(draft, BroadcastRecipientStatus.SKIPPED)).isEqualTo(1);
        assertThat(List.of(completed.getSentTotal(), completed.getFailedTotal(), completed.getExcludedTotal(), completed.getDiscardedTotal()))
                .containsExactly(0, 1, 0, 1);
        assertThat(sentSubjects).isEmpty();
    }

    private List<Long> queuedDeliveryIds(Broadcast broadcast) {
        return jdbc.queryForList("select delivery_id from broadcast_recipient where broadcast_id = ? and status = 'QUEUED' order by id",
                Long.class, broadcast.getId());
    }

    private void markDeliverySentWithoutSync(Long deliveryId) {
        jdbc.update("update notification_delivery set status = 'SENT', sent_at = now() where id = ?", deliveryId);
    }

    @Test
    void jobReconcilesQueuedRecipientWhoseDeliveryWasSentButTheSyncNeverRan() {
        Broadcast broadcast = confirmedBroadcast(1, false);
        markDeliverySentWithoutSync(queuedDeliveryIds(broadcast).get(0));
        assertThat(recipients(broadcast, BroadcastRecipientStatus.QUEUED)).isEqualTo(1);
        BroadcastEmailDispatchJob job = jobWith(10, 0, 100, recordingChannel(realGateway));

        job.dispatchPendingBroadcastEmails();
        job.dispatchPendingBroadcastEmails();

        Broadcast reconciled = reload(broadcast);
        assertThat(reconciled.getStatus()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.SENT)).isEqualTo(1);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.QUEUED)).isZero();
        assertThat(reconciled.getSentTotal()).isEqualTo(1);
        assertThat(auditEntries(broadcast, "BROADCAST_COMPLETED")).isEqualTo(1);
        assertThat(sentSubjects).isEmpty();
    }

    @Test
    void cancellingKeepsAsSentTheRecipientWhoseDeliveryWasAlreadySentBeforeTheSync() {
        Broadcast broadcast = confirmedBroadcast(2, false);
        List<Long> deliveries = queuedDeliveryIds(broadcast);
        markDeliverySentWithoutSync(deliveries.get(0));

        canceller.cancelBroadcast(broadcast.getId(), persistPerson().getId(), "Error en el texto");

        Broadcast cancelled = reload(broadcast);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.SENT)).isEqualTo(1);
        assertThat(recipients(broadcast, BroadcastRecipientStatus.SKIPPED)).isEqualTo(1);
        assertThat(List.of(cancelled.getSentTotal(), cancelled.getFailedTotal(), cancelled.getExcludedTotal(), cancelled.getDiscardedTotal()))
                .containsExactly(1, 0, 0, 1);
    }

    @Test
    void aConfirmedBroadcastWithNoOpenRecipientLeftCompletesInsteadOfStayingConfirmedForever() {
        Broadcast broadcast = confirmedBroadcast(2, false);
        jdbc.update("update notification_delivery set status = 'DISCARDED', status_reason = 'BROADCAST_CANCELLED' where id in (select delivery_id from broadcast_recipient where broadcast_id = ?)",
                broadcast.getId());
        jdbc.update("update broadcast_recipient set status = 'SKIPPED' where broadcast_id = ?", broadcast.getId());

        transactionTemplate.executeWithoutResult(status ->
                progressTracker.completeWhenNoOpenRecipients(broadcastRepository.findByIdForUpdate(broadcast.getId()).orElseThrow(),
                        MutableClock.INSTANCE.instant()));

        Broadcast completed = reload(broadcast);
        assertThat(completed.getStatus()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getDiscardedTotal()).isEqualTo(2);
        assertThat(auditEntries(broadcast, "BROADCAST_COMPLETED")).isEqualTo(1);
    }

    @Test
    void frozenTotalsKeepTheExclusionBreakdownAndAddUpToEveryRecipient() {
        Broadcast broadcast = confirmedBroadcast(2, true);
        drain(jobWith(10, 0, 100, recordingChannel(realGateway)));

        Broadcast completed = reload(broadcast);
        long allRecipients = jdbc.queryForObject("select count(*) from broadcast_recipient where broadcast_id = ?", Long.class, broadcast.getId());
        assertThat(completed.getSentTotal() + completed.getFailedTotal() + completed.getExcludedTotal() + completed.getDiscardedTotal())
                .isEqualTo((int) allRecipients);
        Integer breakdownSum = jdbc.queryForObject("select broadcast_reason_counts_sum(excluded_by_reason) from broadcast where id = ?",
                Integer.class, broadcast.getId());
        assertThat(breakdownSum).isEqualTo(completed.getExcludedTotal()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select (excluded_by_reason ->> 'OPTED_OUT')::int from broadcast where id = ?", Integer.class,
                broadcast.getId())).isEqualTo(1);
    }
}
