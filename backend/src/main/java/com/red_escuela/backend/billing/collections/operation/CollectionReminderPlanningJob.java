package com.red_escuela.backend.billing.collections.operation;

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
    private final Clock clock;

    public CollectionReminderPlanningJob(CollectionReminderPlanningStep planningStep, Clock clock) {
        this.planningStep = planningStep;
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
        CollectionReminderPlanningResult result = planningStep.planCollectionReminders(businessDate);
        long durationMs = System.currentTimeMillis() - start;
        log.info("Planificación de recordatorios de cobranza finalizada: candidatos={}, planificados={}, yaPlanificados={}, sinAvisos={}, fallidos={}, duracion={}ms",
                result.candidates(), result.planned(), result.alreadyPlanned(), result.nothingToSend(), result.failed(), durationMs);
        if (result.failed() > 0) {
            log.warn("La planificación de recordatorios de cobranza terminó con fallas por alumno: fallidos={}, errores={}", result.failed(), result.errors());
        }
        return result;
    }
}
