package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettingsEntity;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegularCertificateSettingsFinder {

    private final RegularCertificateSettingsRepository repository;

    public RegularCertificateSettingsFinder(RegularCertificateSettingsRepository repository) {
        this.repository = repository;
    }

    public RegularCertificateSettings current() {
        return repository.findById(RegularCertificateSettingsEntity.SINGLETON_ID)
                .map(RegularCertificateSettingsEntity::toSettings)
                .orElse(RegularCertificateSettings.DEFAULT);
    }

    @Transactional(readOnly = true)
    public RegularCertificateSettingsView currentView() {
        return repository.findById(RegularCertificateSettingsEntity.SINGLETON_ID)
                .map(entity -> new RegularCertificateSettingsView(entity.toSettings(), entity.getVersion()))
                .orElseGet(() -> new RegularCertificateSettingsView(RegularCertificateSettings.DEFAULT, 0L));
    }

    public record RegularCertificateSettingsView(RegularCertificateSettings settings, long version) {
    }
}
