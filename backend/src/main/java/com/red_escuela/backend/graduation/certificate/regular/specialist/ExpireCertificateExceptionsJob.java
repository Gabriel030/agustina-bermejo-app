package com.red_escuela.backend.graduation.certificate.regular.specialist;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireCertificateExceptionsJob {

    private final CertificateExceptionExpirer expirer;

    public ExpireCertificateExceptionsJob(CertificateExceptionExpirer expirer) {
        this.expirer = expirer;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ExpireCertificateExceptionsJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void expireDueExceptions() {
        expirer.expireDue();
    }
}
