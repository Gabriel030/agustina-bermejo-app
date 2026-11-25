package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireApprovedSubjectsCertificatesJob {

    private final ApprovedSubjectsCertificateExpirer expirer;

    public ExpireApprovedSubjectsCertificatesJob(ApprovedSubjectsCertificateExpirer expirer) {
        this.expirer = expirer;
    }

    @Scheduled(cron = "0 25 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ExpireApprovedSubjectsCertificatesJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void expireDueCertificates() {
        expirer.expireDue();
    }
}
