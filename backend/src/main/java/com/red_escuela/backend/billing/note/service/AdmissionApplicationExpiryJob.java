package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.enrollment.api.AdmissionApplicationExpiryPort;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class AdmissionApplicationExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(AdmissionApplicationExpiryJob.class);

    private final AdmissionApplicationExpiryPort applications;
    private final AdmissionApplicationExpiration expiration;
    private final Clock clock;

    public AdmissionApplicationExpiryJob(AdmissionApplicationExpiryPort applications, AdmissionApplicationExpiration expiration, Clock clock) {
        this.applications = applications;
        this.expiration = expiration;
        this.clock = clock;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @Scheduled(cron = "0 20 0 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "AdmissionApplicationExpiryJob", lockAtMostFor = "PT30M")
    public void expireApplicationsWhoseAdmissionClosedUnpaid() {
        LocalDate today = LocalDate.now(clock);
        List<Long> candidates = applications.findApprovedApplicationIdsWithAdmissionClosedBefore(today);
        int expired = 0;
        int failed = 0;
        for (Long applicationId : candidates) {
            try {
                if (expiration.expireWhenAdmissionClosedAndUnpaid(applicationId)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("No se pudo vencer la solicitud de admisión {}", applicationId, e);
            }
        }
        log.info("Vencimiento de solicitudes de admisión finalizado: fecha={}, candidatas={}, vencidas={}, fallidas={}", today, candidates.size(), expired, failed);
    }
}
