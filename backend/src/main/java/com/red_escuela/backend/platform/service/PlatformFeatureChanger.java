package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.platform.domain.BillingModeStateMachine;
import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.entity.FeatureChangeActorKind;
import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import com.red_escuela.backend.platform.entity.PlatformFeature;
import com.red_escuela.backend.platform.exception.FeatureAlreadyInRequestedStateException;
import com.red_escuela.backend.platform.exception.FeatureNotProvisionedException;
import com.red_escuela.backend.platform.exception.FeatureValueInvalidException;
import com.red_escuela.backend.platform.exception.StaleFeatureVersionException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class PlatformFeatureChanger {

    private final PlatformFeatureRepository featureRepository;
    private final FeatureChangeLogRepository changeLogRepository;
    private final FeatureChangeReasonValidator reasonValidator;
    private final FeatureChangeJsonWriter jsonWriter;
    private final FeatureSnapshotHolder snapshotHolder;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public PlatformFeatureChanger(
            PlatformFeatureRepository featureRepository,
            FeatureChangeLogRepository changeLogRepository,
            FeatureChangeReasonValidator reasonValidator,
            FeatureChangeJsonWriter jsonWriter,
            FeatureSnapshotHolder snapshotHolder,
            CurrentActorFinder currentActorFinder,
            Clock clock) {
        this.featureRepository = featureRepository;
        this.changeLogRepository = changeLogRepository;
        this.reasonValidator = reasonValidator;
        this.jsonWriter = jsonWriter;
        this.snapshotHolder = snapshotHolder;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public FeatureState changeFeature(FeatureChange change) {
        String reason = reasonValidator.requireValidReason(change.reason());
        PlatformFeature feature = featureRepository.findByCodeForUpdate(change.code())
                .orElseThrow(() -> new FeatureNotProvisionedException(change.code()));
        if (change.expectedVersion() != null && change.expectedVersion() != feature.getVersion()) {
            throw new StaleFeatureVersionException(change.code(), change.expectedVersion(), feature.getVersion());
        }
        FeatureState before = feature.toState();
        FeatureState requested = resolveRequestedState(change, feature);
        if (requested.enabled() == before.enabled() && requested.mode() == before.mode()) {
            throw new FeatureAlreadyInRequestedStateException(change.code());
        }
        if (change.code().hasMode()) {
            BillingModeStateMachine.requireTransitionAllowed(before.mode(), requested.mode(), change.clearance());
        }
        Long actorPersonId = currentActorFinder.currentPersonId();
        LocalDateTime changedAt = LocalDateTime.now(clock);
        feature.changeTo(requested.enabled(), requested.mode(), changedAt, actorPersonId);
        FeatureState after = featureRepository.saveAndFlush(feature).toState();
        changeLogRepository.save(FeatureChangeLog.builder()
                .code(change.code())
                .beforeJson(jsonWriter.writeState(before))
                .afterJson(jsonWriter.writeState(after))
                .actorPersonId(actorPersonId)
                .actorKind(FeatureChangeActorKind.HUMAN)
                .reason(reason)
                .changedAt(changedAt)
                .build());
        refreshSnapshotAfterCommit();
        return after;
    }

    private FeatureState resolveRequestedState(FeatureChange change, PlatformFeature feature) {
        if (change.code().hasMode()) {
            if (change.mode() == null) {
                throw new FeatureValueInvalidException(change.code(), "falta el modo");
            }
            if (change.enabled() != null && change.enabled() != change.mode().isEnabled()) {
                throw new FeatureValueInvalidException(change.code(), "enabled no coincide con el modo " + change.mode());
            }
            return FeatureState.billing(change.mode(), feature.getVersion());
        }
        if (change.mode() != null) {
            throw new FeatureValueInvalidException(change.code(), "esta feature no tiene modo");
        }
        if (change.enabled() == null) {
            throw new FeatureValueInvalidException(change.code(), "falta enabled");
        }
        return new FeatureState(change.enabled(), null, feature.getVersion());
    }

    private void refreshSnapshotAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                snapshotHolder.refresh();
            }
        });
    }
}
