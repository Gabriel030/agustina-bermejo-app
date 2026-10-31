package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.entity.FeatureChangeActorKind;
import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import com.red_escuela.backend.platform.entity.PlatformFeature;
import com.red_escuela.backend.platform.exception.FeatureNotProvisionedException;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class InitialBillingModeProvisioner {

    public static final String PROVISIONING_REASON = "Aprovisionamiento inicial del instituto";

    private final PlatformFeatureRepository featureRepository;
    private final FeatureChangeLogRepository changeLogRepository;
    private final FeatureChangeJsonWriter jsonWriter;
    private final Clock clock;

    public InitialBillingModeProvisioner(
            PlatformFeatureRepository featureRepository,
            FeatureChangeLogRepository changeLogRepository,
            FeatureChangeJsonWriter jsonWriter,
            Clock clock) {
        this.featureRepository = featureRepository;
        this.changeLogRepository = changeLogRepository;
        this.jsonWriter = jsonWriter;
        this.clock = clock;
    }

    @Transactional
    public boolean provisionInitialBillingMode(BillingMode initialMode) {
        PlatformFeature billing = featureRepository.findByCodeForUpdate(FeatureCode.BILLING)
                .orElseThrow(() -> new FeatureNotProvisionedException(FeatureCode.BILLING));
        boolean neverChanged = billing.getVersion() == 0 && !changeLogRepository.existsByCode(FeatureCode.BILLING);
        if (!neverChanged || billing.getMode() == initialMode) {
            return false;
        }
        FeatureState before = billing.toState();
        LocalDateTime changedAt = LocalDateTime.now(clock);
        billing.changeTo(initialMode.isEnabled(), initialMode, changedAt, null);
        FeatureState after = featureRepository.saveAndFlush(billing).toState();
        changeLogRepository.save(FeatureChangeLog.builder()
                .code(FeatureCode.BILLING)
                .beforeJson(jsonWriter.writeState(before))
                .afterJson(jsonWriter.writeState(after))
                .actorKind(FeatureChangeActorKind.SERVICE)
                .reason(PROVISIONING_REASON)
                .changedAt(changedAt)
                .build());
        return true;
    }
}
