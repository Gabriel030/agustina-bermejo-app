package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InitialBillingModeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialBillingModeRunner.class);

    private final InitialBillingModeProvisioner provisioner;
    private final FeatureSnapshotHolder snapshotHolder;
    private final BillingMode initialMode;

    public InitialBillingModeRunner(
            InitialBillingModeProvisioner provisioner,
            FeatureSnapshotHolder snapshotHolder,
            @Value("${platform.provisioning.initial-billing-mode:OFF}") BillingMode initialMode) {
        this.provisioner = provisioner;
        this.snapshotHolder = snapshotHolder;
        this.initialMode = initialMode;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (provisioner.provisionInitialBillingMode(initialMode)) {
            log.info("billing_mode_provisioned mode={}", initialMode);
        }
        snapshotHolder.refresh();
    }
}
