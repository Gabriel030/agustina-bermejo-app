package com.red_escuela.backend.graduation.certificate.regular.repository;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegularCertificateSettingsRepository extends JpaRepository<RegularCertificateSettingsEntity, Short> {
}
