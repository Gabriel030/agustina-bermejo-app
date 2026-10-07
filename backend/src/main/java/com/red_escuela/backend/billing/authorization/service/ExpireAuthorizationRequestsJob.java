package com.red_escuela.backend.billing.authorization.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireAuthorizationRequestsJob {
    private final AuthorizationRequestExpirer expirer;

    public ExpireAuthorizationRequestsJob(AuthorizationRequestExpirer expirer) {
        this.expirer = expirer;
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ExpireAuthorizationRequestsJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void expireDueRequests() {
        expirer.expireDueRequests();
    }
}
