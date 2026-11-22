package com.red_escuela.backend.graduation.documentsettings.specialist;

import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.entity.DocumentIssuanceSettingsEntity;
import com.red_escuela.backend.graduation.documentsettings.repository.DocumentIssuanceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIssuanceSettingsFinder {

    private final DocumentIssuanceSettingsRepository repository;

    public DocumentIssuanceSettingsFinder(DocumentIssuanceSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DocumentIssuanceSettingsResponse current() {
        return DocumentIssuanceSettingsUpdater.responseOf(repository.findById(DocumentIssuanceSettingsEntity.SINGLETON_ID).orElseThrow());
    }
}
