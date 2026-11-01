package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.platform.entity.FeatureChangeActorKind;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InitialBillingModeProvisionerIT extends AbstractIntegrationTest {

    @Autowired
    private InitialBillingModeProvisioner provisioner;

    @Autowired
    private PlatformFeatureRepository featureRepository;

    @Autowired
    private FeatureChangeLogRepository changeLogRepository;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restoreSeedState() {
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        jdbc.update("delete from feature_change_log");
        snapshotHolder.refresh();
    }

    @Test
    void aNeverTouchedInstituteIsMovedToTheConfiguredInitialModeAndTheServiceActorIsAudited() {
        assertThat(provisioner.provisionInitialBillingMode(BillingMode.OFF)).isTrue();

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.OFF);
        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().isEnabled()).isFalse();
        assertThat(changeLogRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getActorKind()).isEqualTo(FeatureChangeActorKind.SERVICE);
            assertThat(entry.getReason()).isEqualTo(InitialBillingModeProvisioner.PROVISIONING_REASON);
            assertThat(entry.getBeforeJson()).contains("FULL");
            assertThat(entry.getAfterJson()).contains("OFF");
        });
    }

    @Test
    void provisioningTwiceChangesNothingTheSecondTime() {
        provisioner.provisionInitialBillingMode(BillingMode.OFF);

        assertThat(provisioner.provisionInitialBillingMode(BillingMode.OFF)).isFalse();
        assertThat(provisioner.provisionInitialBillingMode(BillingMode.FULL)).isFalse();

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.OFF);
        assertThat(changeLogRepository.findAll()).hasSize(1);
    }

    @Test
    void anInstituteAlreadyInTheInitialModeIsLeftUntouchedAndUnaudited() {
        assertThat(provisioner.provisionInitialBillingMode(BillingMode.FULL)).isFalse();

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getVersion()).isZero();
        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    @Test
    void anInstituteThatAlreadyHadAHumanChangeIsNeverOverriddenByProvisioning() {
        jdbc.update("update platform_feature set version = 3 where code = 'BILLING'");

        assertThat(provisioner.provisionInitialBillingMode(BillingMode.OFF)).isFalse();

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
    }
}
