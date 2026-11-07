package com.red_escuela.backend.graduation.certificate.regular.specialist;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireRegularCertificatesJob {

    private final RegularCertificateExpirer expirer;

    public ExpireRegularCertificatesJob(RegularCertificateExpirer expirer) {
        this.expirer = expirer;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ExpireRegularCertificatesJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void expireDueCertificates() {
        expirer.expireDue();
    }
}
