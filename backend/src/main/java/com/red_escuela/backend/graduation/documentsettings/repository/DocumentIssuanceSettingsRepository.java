package com.red_escuela.backend.graduation.documentsettings.repository;

import com.red_escuela.backend.graduation.documentsettings.entity.DocumentIssuanceSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIssuanceSettingsRepository extends JpaRepository<DocumentIssuanceSettingsEntity, Short> {
}
