package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.platform.domain.ActorSummary;
import com.red_escuela.backend.platform.domain.InstituteSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettingState;
import com.red_escuela.backend.platform.entity.InstituteSetting;
import com.red_escuela.backend.platform.entity.InstituteSettingChangeLog;
import com.red_escuela.backend.platform.exception.InstituteSettingAlreadyInRequestedStateException;
import com.red_escuela.backend.platform.exception.InstituteSettingNotProvisionedException;
import com.red_escuela.backend.platform.exception.LastDigitalPaymentMethodRequiredException;
import com.red_escuela.backend.platform.exception.StaleInstituteSettingVersionException;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.platform.repository.InstituteSettingChangeLogRepository;
import com.red_escuela.backend.platform.repository.InstituteSettingRepository;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import com.red_escuela.backend.shared.module.OnlinePaymentDependencyPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
public class InstituteSettingChanger {

    private final InstituteSettingRepository settingRepository;
    private final InstituteSettingChangeLogRepository changeLogRepository;
    private final PlatformFeatureRepository featureRepository;
    private final FeatureChangeReasonValidator reasonValidator;
    private final InstituteSettingsSnapshotHolder snapshotHolder;
    private final CurrentActorFinder currentActorFinder;
    private final ActorSummaryFinder actorSummaryFinder;
    private final OnlinePaymentDependencyPort onlinePaymentDependencies;
    private final Clock clock;

    public InstituteSettingChanger(
            InstituteSettingRepository settingRepository,
            InstituteSettingChangeLogRepository changeLogRepository,
            PlatformFeatureRepository featureRepository,
            FeatureChangeReasonValidator reasonValidator,
            InstituteSettingsSnapshotHolder snapshotHolder,
            CurrentActorFinder currentActorFinder,
            ActorSummaryFinder actorSummaryFinder,
            OnlinePaymentDependencyPort onlinePaymentDependencies,
            Clock clock) {
        this.settingRepository = settingRepository;
        this.changeLogRepository = changeLogRepository;
        this.featureRepository = featureRepository;
        this.reasonValidator = reasonValidator;
        this.snapshotHolder = snapshotHolder;
        this.currentActorFinder = currentActorFinder;
        this.actorSummaryFinder = actorSummaryFinder;
        this.onlinePaymentDependencies = onlinePaymentDependencies;
        this.clock = clock;
    }

    @Transactional
    public InstituteSettingState change(InstituteSettingChange change) {
        String reason = reasonValidator.requireValidReason(change.reason());
        InstituteSetting setting = settingRepository.findByCodeForUpdate(change.code())
                .orElseThrow(() -> new InstituteSettingNotProvisionedException(change.code()));
        if (change.expectedVersion() != null && change.expectedVersion() != setting.getVersion()) {
            throw new StaleInstituteSettingVersionException(change.code(), change.expectedVersion(), setting.getVersion(), setting.getUpdatedAt(),
                    setting.getUpdatedById(), actorNameOf(setting.getUpdatedById()));
        }
        boolean before = setting.isEnabled();
        if (before == change.enabled()) {
            throw new InstituteSettingAlreadyInRequestedStateException(change.code());
        }
        if (!change.enabled()) {
            requireAnotherDigitalMethodWhileOnlineInvoicesAreOpen(change.code());
        }
        Long actorPersonId = currentActorFinder.currentPersonId();
        LocalDateTime changedAt = LocalDateTime.now(clock);
        setting.changeTo(change.enabled(), changedAt, actorPersonId);
        InstituteSettingState after = settingRepository.saveAndFlush(setting).toState();
        changeLogRepository.save(InstituteSettingChangeLog.builder()
                .code(change.code())
                .beforeEnabled(before)
                .afterEnabled(change.enabled())
                .actorPersonId(actorPersonId)
                .reason(reason)
                .changedAt(changedAt)
                .build());
        refreshSnapshotAfterCommit();
        return after;
    }

    private void requireAnotherDigitalMethodWhileOnlineInvoicesAreOpen(InstituteSettingCode code) {
        PaymentMethodCode method = paymentMethodOf(code);
        if (method == null || !method.isDigital() || !isUsable(method)) {
            return;
        }
        boolean anotherDigitalMethodIsUsable = Arrays.stream(PaymentMethodCode.values())
                .filter(other -> other != method && other.isDigital())
                .anyMatch(this::isUsable);
        if (anotherDigitalMethodIsUsable) {
            return;
        }
        long openOnlineInvoices = onlinePaymentDependencies.countOpenOnlineDependencies();
        if (openOnlineInvoices > 0) {
            throw new LastDigitalPaymentMethodRequiredException(method, openOnlineInvoices);
        }
    }

    private PaymentMethodCode paymentMethodOf(InstituteSettingCode code) {
        return Arrays.stream(PaymentMethodCode.values()).filter(method -> method.settingCode() == code).findFirst().orElse(null);
    }

    private boolean isUsable(PaymentMethodCode method) {
        boolean platformEnabled = featureRepository.findById(method.featureCode()).map(feature -> feature.isEnabled()).orElse(false);
        boolean instituteEnabled = settingRepository.findById(method.settingCode()).map(InstituteSetting::isEnabled).orElse(false);
        return platformEnabled && instituteEnabled;
    }

    private String actorNameOf(Long personId) {
        ActorSummary actor = actorSummaryFinder.find(personId);
        return actor == null ? null : actor.fullName();
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
