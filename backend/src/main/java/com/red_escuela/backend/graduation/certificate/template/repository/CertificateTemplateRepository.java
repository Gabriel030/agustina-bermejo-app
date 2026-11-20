package com.red_escuela.backend.graduation.certificate.template.repository;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, Long> {

    Optional<CertificateTemplate> findByKind(GraduationCertificateKind kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CertificateTemplate t where t.kind = :kind")
    Optional<CertificateTemplate> findByKindForUpdate(@Param("kind") GraduationCertificateKind kind);
}
