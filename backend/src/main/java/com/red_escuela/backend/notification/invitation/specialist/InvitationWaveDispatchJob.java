package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationSendResult;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.exception.InvitationSendDeferredException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import com.red_escuela.backend.shared.scheduling.ScheduledJobFailureCounter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@Component
public class InvitationWaveDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(InvitationWaveDispatchJob.class);
    private static final String JOB_NAME = "InvitationWaveDispatchJob";
    private static final List<InvitationRecipientStatus> OPEN_STATUSES = List.of(InvitationRecipientStatus.PENDING, InvitationRecipientStatus.QUEUED);

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;
    private final InvitationRecipientSendTransaction sendTransaction;
    private final InvitationWaveLifecycleTransaction lifecycleTransaction;
    private final InvitationSendingCapacity capacity;
    private final BroadcastDispatchSettings dispatchSettings;
    private final Clock clock;
    private final ScheduledJobFailureCounter failureCounter;

    public InvitationWaveDispatchJob(InvitationWaveRepository waveRepository, InvitationWaveRecipientRepository recipientRepository,
                                     InvitationRecipientSendTransaction sendTransaction, InvitationWaveLifecycleTransaction lifecycleTransaction,
                                     InvitationSendingCapacity capacity, BroadcastDispatchSettings dispatchSettings, Clock clock,
                                     MeterRegistry meterRegistry) {
        this.waveRepository = waveRepository;
        this.recipientRepository = recipientRepository;
        this.sendTransaction = sendTransaction;
        this.lifecycleTransaction = lifecycleTransaction;
        this.capacity = capacity;
        this.dispatchSettings = dispatchSettings;
        this.clock = clock;
        this.failureCounter = new ScheduledJobFailureCounter(meterRegistry);
    }

    @Scheduled(cron = "0 * * * * *", zone = "${app.timezone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT10M", lockAtLeastFor = "PT20S")
    public void run() {
        dispatchRunningInvitationWaves();
    }

    public int dispatchRunningInvitationWaves() {
        if (!isWithinSendWindow()) {
            return 0;
        }
        int sent = 0;
        for (InvitationWave wave : waveRepository.findByStatusOrderByIdAsc(InvitationWaveStatus.RUNNING)) {
            if (!isWithinSendWindow()) {
                break;
            }
            try {
                sent += dispatchWave(wave);
            } catch (RuntimeException failure) {
                failureCounter.record(JOB_NAME, failure);
                log.warn("Fallo el despacho de la oleada waveId={} causa={}", wave.getId(), failure.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private int dispatchWave(InvitationWave wave) {
        if (hasNoOpenRecipients(wave)) {
            completeWave(wave);
            return 0;
        }
        if (!isDue(wave)) {
            return 0;
        }
        int sent = sendBatch(wave);
        if (hasNoOpenRecipients(wave)) {
            completeWave(wave);
        }
        return sent;
    }

    private int sendBatch(InvitationWave wave) {
        List<Long> recipientIds = recipientRepository.findIdsByWaveIdAndStatusIn(wave.getId(), OPEN_STATUSES, PageRequest.of(0, wave.getBatchSize()));
        int sent = 0;
        for (Long recipientId : recipientIds) {
            if (!isWithinSendWindow() || !capacity.hasCapacityToday(wave)) {
                break;
            }
            InvitationSendResult result = sendOne(recipientId);
            if (result == InvitationSendResult.DEFERRED || result == InvitationSendResult.NO_QUOTA) {
                log.info("Se corta la tanda de la oleada waveId={} motivo={}", wave.getId(), result);
                break;
            }
            if (result == InvitationSendResult.SENT) {
                sent++;
            }
        }
        return sent;
    }

    private InvitationSendResult sendOne(Long recipientId) {
        try {
            return sendTransaction.sendInvitationToRecipient(recipientId);
        } catch (InvitationSendDeferredException deferred) {
            return InvitationSendResult.DEFERRED;
        } catch (RuntimeException failure) {
            failureCounter.record(JOB_NAME, failure);
            log.warn("Fallo el envio de la invitacion recipientId={} causa={}", recipientId, failure.getClass().getSimpleName());
            return InvitationSendResult.NOT_SENDABLE;
        }
    }

    private boolean isDue(InvitationWave wave) {
        Instant now = Instant.now(clock);
        return recipientRepository.findLastSentAt(wave.getId())
                .map(lastSentAt -> !lastSentAt.plus(Duration.ofMinutes(wave.getIntervalMinutes())).isAfter(now))
                .orElse(true);
    }

    private boolean hasNoOpenRecipients(InvitationWave wave) {
        return recipientRepository.countByWaveIdAndStatusIn(wave.getId(), OPEN_STATUSES) == 0;
    }

    private void completeWave(InvitationWave wave) {
        lifecycleTransaction.completeInvitationWave(wave.getId());
        log.info("Oleada de invitaciones completada waveId={}", wave.getId());
    }

    private boolean isWithinSendWindow() {
        return dispatchSettings.window().isOpenAt(LocalTime.now(clock));
    }
}
