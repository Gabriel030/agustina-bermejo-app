package com.red_escuela.backend.enrollment.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AdmissionEnrollmentStalledJob {
    private final AdmissionEnrollmentStalledNotifier notifier;

    public AdmissionEnrollmentStalledJob(AdmissionEnrollmentStalledNotifier notifier) {
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M", zone = "${app.timezone}")
    @SchedulerLock(name = "AdmissionEnrollmentStalledJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void notifyStalledEnrollments() {
        notifier.notifySecretariatOfStalledEnrollments();
    }
}
