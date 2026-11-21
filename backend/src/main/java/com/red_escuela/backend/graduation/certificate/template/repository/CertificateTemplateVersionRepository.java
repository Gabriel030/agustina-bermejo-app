package com.red_escuela.backend.graduation.certificate.template.repository;

import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateTemplateVersionRepository extends JpaRepository<CertificateTemplateVersion, Long> {

    Optional<CertificateTemplateVersion> findByTemplateIdAndStatus(Long templateId, TemplateVersionStatus status);

    Optional<CertificateTemplateVersion> findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(Long templateId, TemplateVersionStatus status);

    List<CertificateTemplateVersion> findByTemplateIdAndStatusOrderByVersionNumberDesc(Long templateId, TemplateVersionStatus status);

    Optional<CertificateTemplateVersion> findByTemplateIdAndVersionNumber(Long templateId, Integer versionNumber);
}
