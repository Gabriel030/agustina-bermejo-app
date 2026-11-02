package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireAuthorizationRequestsJob {
    private final AuthorizationRequestExpirer expirer;

    public ExpireAuthorizationRequestsJob(AuthorizationRequestExpirer expirer) {
        this.expirer = expirer;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @Scheduled(cron = "0 15 0 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ExpireAuthorizationRequestsJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void expireDueRequests() {
        expirer.expireDueRequests();
    }
}
