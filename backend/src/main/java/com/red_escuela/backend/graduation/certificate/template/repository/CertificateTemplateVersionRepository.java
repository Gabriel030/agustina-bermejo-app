package com.red_escuela.backend.graduation.certificate.template.repository;

import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CertificateTemplateVersionRepository extends JpaRepository<CertificateTemplateVersion, Long> {

    Optional<CertificateTemplateVersion> findByTemplateIdAndStatus(Long templateId, TemplateVersionStatus status);

    Optional<CertificateTemplateVersion> findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(Long templateId, TemplateVersionStatus status);

    List<CertificateTemplateVersion> findByTemplateIdAndStatusOrderByVersionNumberDesc(Long templateId, TemplateVersionStatus status);

    Optional<CertificateTemplateVersion> findByTemplateIdAndVersionNumber(Long templateId, Integer versionNumber);

    @Query("select v from CertificateTemplateVersion v where v.signatory1Id in :ids or v.signatory2Id in :ids")
    List<CertificateTemplateVersion> findUsingSignatories(@Param("ids") Collection<Long> ids);
}
