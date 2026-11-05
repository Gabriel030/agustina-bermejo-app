package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivateDueConfigurationVersionsJob {
    private final ConfigurationVersionActivator activator;

    public ActivateDueConfigurationVersionsJob(ConfigurationVersionActivator activator) {
        this.activator = activator;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @Scheduled(cron = "0 5 0 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "ActivateDueConfigurationVersionsJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void activateDueVersions() {
        activator.activateDueVersions();
    }
}
