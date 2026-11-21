package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettingsEntity;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateSettingsRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateSettingsFinder.RegularCertificateSettingsView;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RegularCertificateSettingsUpdater {

    private final RegularCertificateSettingsRepository repository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public RegularCertificateSettingsUpdater(RegularCertificateSettingsRepository repository, CurrentActorFinder currentActorFinder,
                                             Clock clock) {
        this.repository = repository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public RegularCertificateSettingsView update(RegularCertificateSettings settings, long expectedVersion) {
        Long actorId = currentActorFinder.currentPersonId();
        LocalDateTime now = LocalDateTime.now(clock);
        RegularCertificateSettingsEntity entity = repository.findById(RegularCertificateSettingsEntity.SINGLETON_ID).orElse(null);
        if (entity == null) {
            requireVersion(0L, expectedVersion);
            entity = RegularCertificateSettingsEntity.from(settings);
            entity.apply(settings, actorId, now);
        } else {
            requireVersion(entity.getVersion(), expectedVersion);
            entity.apply(settings, actorId, now);
        }
        RegularCertificateSettingsEntity saved = repository.saveAndFlush(entity);
        return new RegularCertificateSettingsView(saved.toSettings(), saved.getVersion());
    }

    private void requireVersion(long currentVersion, long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(RegularCertificateSettingsEntity.class,
                    RegularCertificateSettingsEntity.SINGLETON_ID);
        }
    }
}
