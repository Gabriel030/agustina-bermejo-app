package com.red_escuela.backend.notification.invitation;

import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.invitation.domain.InvitationSendResult;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.exception.InvitationSendDeferredException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import com.red_escuela.backend.notification.invitation.specialist.InvitationRecipientSendTransaction;
import com.red_escuela.backend.notification.invitation.specialist.InvitationSendingCapacity;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveDispatchJob;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveLifecycleTransaction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvitationWaveDispatchJobTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant NOON = Instant.parse("2026-11-23T15:00:00Z");

    private InvitationWaveRepository waveRepository;
    private InvitationWaveRecipientRepository recipientRepository;
    private InvitationRecipientSendTransaction sendTransaction;
    private InvitationWaveLifecycleTransaction lifecycleTransaction;
    private InvitationSendingCapacity capacity;
    private BroadcastDispatchSettings dispatchSettings;
    private List<Long> openRecipientIds;

    @BeforeEach
    void setUp() {
        waveRepository = mock(InvitationWaveRepository.class);
        recipientRepository = mock(InvitationWaveRecipientRepository.class);
        sendTransaction = mock(InvitationRecipientSendTransaction.class);
        lifecycleTransaction = mock(InvitationWaveLifecycleTransaction.class);
        capacity = mock(InvitationSendingCapacity.class);
        dispatchSettings = new BroadcastDispatchSettings(300, 100, 100, "09:00", "20:00");
        openRecipientIds = new ArrayList<>(List.of(1L, 2L, 3L, 4L, 5L));
        when(capacity.hasCapacityToday(any())).thenReturn(true);
        when(recipientRepository.findLastSentAt(anyLong())).thenReturn(Optional.empty());
        when(recipientRepository.countByWaveIdAndStatusIn(anyLong(), anyCollection())).thenAnswer(call -> (long) openRecipientIds.size());
        when(recipientRepository.findIdsByWaveIdAndStatusIn(anyLong(), anyCollection(), any(Pageable.class))).thenAnswer(call -> {
            Pageable page = call.getArgument(2);
            return new ArrayList<>(openRecipientIds.subList(0, Math.min(page.getPageSize(), openRecipientIds.size())));
        });
        when(sendTransaction.sendInvitationToRecipient(anyLong())).thenAnswer(call -> {
            openRecipientIds.remove(call.<Object>getArgument(0));
            return InvitationSendResult.SENT;
        });
    }

    private InvitationWaveDispatchJob jobAt(Instant now) {
        return new InvitationWaveDispatchJob(waveRepository, recipientRepository, sendTransaction, lifecycleTransaction, capacity,
                dispatchSettings, Clock.fixed(now, ZONE), new SimpleMeterRegistry());
    }

    private InvitationWave runningWave(long id, int batchSize, int intervalMinutes) {
        return InvitationWave.builder().id(id).status(InvitationWaveStatus.RUNNING).batchSize(batchSize).intervalMinutes(intervalMinutes)
                .dailyCap(300).build();
    }

    private void runningWaves(InvitationWave... waves) {
        when(waveRepository.findByStatusOrderByIdAsc(InvitationWaveStatus.RUNNING)).thenReturn(List.of(waves));
    }

    @Test
    void sendsAtMostBatchSizeRecipientsPerRun() {
        runningWaves(runningWave(10L, 3, 5));

        int sent = jobAt(NOON).dispatchRunningInvitationWaves();

        assertThat(sent).isEqualTo(3);
        verify(sendTransaction, times(3)).sendInvitationToRecipient(anyLong());
        verify(sendTransaction, never()).sendInvitationToRecipient(4L);
        verify(lifecycleTransaction, never()).completeInvitationWave(anyLong());
    }

    @Test
    void doesNotSendBeforeTheIntervalElapsed() {
        runningWaves(runningWave(10L, 3, 10));
        when(recipientRepository.findLastSentAt(10L)).thenReturn(Optional.of(NOON.minusSeconds(9 * 60)));

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isZero();

        verifyNoInteractions(sendTransaction);
    }

    @Test
    void sendsAgainWhenTheIntervalElapsedExactly() {
        runningWaves(runningWave(10L, 2, 10));
        when(recipientRepository.findLastSentAt(10L)).thenReturn(Optional.of(NOON.minusSeconds(10 * 60)));

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isEqualTo(2);
    }

    @Test
    void stopsTheBatchWhenTheWaveOrGlobalCapacityIsExhausted() {
        InvitationWave wave = runningWave(10L, 5, 5);
        runningWaves(wave);
        when(capacity.hasCapacityToday(wave)).thenReturn(true, true, false);

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isEqualTo(2);

        verify(sendTransaction, times(2)).sendInvitationToRecipient(anyLong());
        verify(lifecycleTransaction, never()).completeInvitationWave(anyLong());
    }

    @Test
    void checksCapacityBeforeEverySend() {
        InvitationWave wave = runningWave(10L, 3, 5);
        runningWaves(wave);

        jobAt(NOON).dispatchRunningInvitationWaves();

        var order = inOrder(capacity, sendTransaction);
        order.verify(capacity).hasCapacityToday(wave);
        order.verify(sendTransaction).sendInvitationToRecipient(1L);
        order.verify(capacity).hasCapacityToday(wave);
        order.verify(sendTransaction).sendInvitationToRecipient(2L);
        order.verify(capacity).hasCapacityToday(wave);
        order.verify(sendTransaction).sendInvitationToRecipient(3L);
    }

    @Test
    void doesNotSendBeforeTheWindowOpens() {
        runningWaves(runningWave(10L, 3, 5));

        assertThat(jobAt(Instant.parse("2026-11-23T11:59:00Z")).dispatchRunningInvitationWaves()).isZero();

        verifyNoInteractions(sendTransaction, waveRepository);
    }

    @Test
    void doesNotSendOnceTheWindowCloses() {
        runningWaves(runningWave(10L, 3, 5));

        assertThat(jobAt(Instant.parse("2026-11-23T23:00:00Z")).dispatchRunningInvitationWaves()).isZero();

        verifyNoInteractions(sendTransaction, waveRepository);
    }

    @Test
    void sendsRightAtTheWindowStart() {
        runningWaves(runningWave(10L, 1, 5));

        assertThat(jobAt(Instant.parse("2026-11-23T12:00:00Z")).dispatchRunningInvitationWaves()).isEqualTo(1);
    }

    @Test
    void openCircuitCutsTheBatchWithoutMarkingAnyoneAsFailed() {
        runningWaves(runningWave(10L, 5, 5));
        doThrow(new InvitationSendDeferredException(2L)).when(sendTransaction).sendInvitationToRecipient(2L);

        int sent = jobAt(NOON).dispatchRunningInvitationWaves();

        assertThat(sent).isEqualTo(1);
        verify(sendTransaction).sendInvitationToRecipient(1L);
        verify(sendTransaction).sendInvitationToRecipient(2L);
        verify(sendTransaction, never()).sendInvitationToRecipient(3L);
        verify(lifecycleTransaction, never()).completeInvitationWave(anyLong());
    }

    @Test
    void deferredResultAlsoCutsTheBatch() {
        runningWaves(runningWave(10L, 5, 5));
        doReturn(InvitationSendResult.DEFERRED).when(sendTransaction).sendInvitationToRecipient(1L);

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isZero();

        verify(sendTransaction, times(1)).sendInvitationToRecipient(anyLong());
    }

    @Test
    void noQuotaResultCutsTheBatch() {
        runningWaves(runningWave(10L, 5, 5));
        doReturn(InvitationSendResult.NO_QUOTA).when(sendTransaction).sendInvitationToRecipient(1L);

        jobAt(NOON).dispatchRunningInvitationWaves();

        verify(sendTransaction, times(1)).sendInvitationToRecipient(anyLong());
    }

    @Test
    void completesTheWaveWhenTheLastOpenRecipientIsSent() {
        openRecipientIds = new ArrayList<>(List.of(1L, 2L));
        runningWaves(runningWave(10L, 5, 5));

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isEqualTo(2);

        verify(lifecycleTransaction).completeInvitationWave(10L);
    }

    @Test
    void completesAWaveWithoutOpenRecipientsEvenBeforeTheIntervalElapsed() {
        openRecipientIds = new ArrayList<>();
        runningWaves(runningWave(10L, 5, 60));
        when(recipientRepository.findLastSentAt(10L)).thenReturn(Optional.of(NOON.minusSeconds(60)));

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isZero();

        verify(lifecycleTransaction).completeInvitationWave(10L);
        verifyNoInteractions(sendTransaction);
    }

    @Test
    void onlyRunningWavesAreDispatched() {
        runningWaves();

        jobAt(NOON).dispatchRunningInvitationWaves();

        verify(waveRepository).findByStatusOrderByIdAsc(InvitationWaveStatus.RUNNING);
        verify(waveRepository, never()).findByStatusOrderByIdAsc(InvitationWaveStatus.PAUSED);
        verify(waveRepository, never()).findByStatusOrderByIdAsc(InvitationWaveStatus.CANCELLED);
        verify(waveRepository, never()).findByStatusOrderByIdAsc(InvitationWaveStatus.DRAFT);
        verifyNoInteractions(sendTransaction, lifecycleTransaction);
    }

    @Test
    void aFailingWaveDoesNotStopTheOthers() {
        InvitationWave broken = runningWave(10L, 2, 5);
        InvitationWave healthy = runningWave(11L, 2, 5);
        runningWaves(broken, healthy);
        when(recipientRepository.findLastSentAt(10L)).thenThrow(new IllegalStateException("boom"));

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isEqualTo(2);
    }

    @Test
    void anUnexpectedSendFailureDoesNotAbortTheRestOfTheBatch() {
        runningWaves(runningWave(10L, 3, 5));
        doThrow(new IllegalStateException("boom")).when(sendTransaction).sendInvitationToRecipient(1L);

        assertThat(jobAt(NOON).dispatchRunningInvitationWaves()).isEqualTo(2);

        verify(sendTransaction).sendInvitationToRecipient(eq(3L));
    }

    @Test
    void theJobIsScheduledEveryMinuteUnderAClusterLock() throws NoSuchMethodException {
        var method = InvitationWaveDispatchJob.class.getMethod("run");

        assertThat(method.getAnnotation(Scheduled.class).cron()).isEqualTo("0 * * * * *");
        assertThat(method.getAnnotation(SchedulerLock.class).name()).isEqualTo("InvitationWaveDispatchJob");
        assertThat(method.getAnnotation(SchedulerLock.class).lockAtMostFor()).isEqualTo("PT10M");
    }
}
