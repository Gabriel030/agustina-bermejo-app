package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRunStore;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class CollectionReminderPlanningJob {
    public static final String CRON = "0 0 5 * * *";
    private static final Logger log = LoggerFactory.getLogger(CollectionReminderPlanningJob.class);

    private final CollectionReminderPlanningStep planningStep;
    private final OverdueContactListPublicationStep contactListStep;
    private final PostWithdrawalSummaryPlanningStep postWithdrawalSummaryStep;
    private final MissedReminderDaysFinder missedDaysFinder;
    private final CollectionReminderRunStore runStore;
    private final Clock clock;

    public CollectionReminderPlanningJob(CollectionReminderPlanningStep planningStep, OverdueContactListPublicationStep contactListStep,
                                          PostWithdrawalSummaryPlanningStep postWithdrawalSummaryStep, MissedReminderDaysFinder missedDaysFinder,
                                          CollectionReminderRunStore runStore, Clock clock) {
        this.missedDaysFinder = missedDaysFinder;
        this.runStore = runStore;
        this.planningStep = planningStep;
        this.contactListStep = contactListStep;
        this.postWithdrawalSummaryStep = postWithdrawalSummaryStep;
        this.clock = clock;
    }

    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "CollectionReminderPlanningJob", lockAtMostFor = "PT30M")
    public void run() {
        run(LocalDate.now(clock));
    }

    public CollectionReminderPlanningResult run(LocalDate businessDate) {
        log.info("Planificación de recordatorios de cobranza iniciada: fecha={}", businessDate);
        long start = System.currentTimeMillis();
        MissedReminderRecovery recovery = missedDaysFinder.findRecoveryBefore(businessDate);
        if (recovery.isRecovering()) {
            log.info("Recuperación de recordatorios de cobranza: fecha={}, diasSinCorrida={}", businessDate, recovery.missedDays());
        }
        CollectionReminderPlanningResult result = planningStep.planCollectionReminders(businessDate, recovery);
        long durationMs = System.currentTimeMillis() - start;
        log.info("Planificación de recordatorios de cobranza finalizada: candidatos={}, planificados={}, yaPlanificados={}, sinAvisos={}, fallidos={}, duracion={}ms",
                result.candidates(), result.planned(), result.alreadyPlanned(), result.nothingToSend(), result.failed(), durationMs);
        if (result.failed() > 0) {
            log.warn("La planificación de recordatorios de cobranza terminó con fallas por alumno: fallidos={}, errores={}", result.failed(), result.errors());
        }
        if (result.failed() == 0) {
            runStore.recordCoveredDay(businessDate);
            recovery.missedDays().forEach(runStore::recordCoveredDay);
        }
        publishContactList(businessDate);
        planPostWithdrawalSummaries(businessDate);
        return result;
    }

    private void publishContactList(LocalDate businessDate) {
        try {
            contactListStep.publishContactListOf(businessDate);
        } catch (RuntimeException failure) {
            log.warn("Falló la publicación de la lista para contactar: fecha={}", businessDate, failure);
        }
    }

    private void planPostWithdrawalSummaries(LocalDate businessDate) {
        try {
            postWithdrawalSummaryStep.planPostWithdrawalSummariesOf(businessDate);
        } catch (RuntimeException failure) {
            log.warn("Falló la planificación de los resúmenes post-baja: fecha={}", businessDate, failure);
        }
    }
}
