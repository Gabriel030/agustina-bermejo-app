package com.red_escuela.backend.graduation.certificate.signatory.repository;

import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CertificateSignatoryRepository extends JpaRepository<CertificateSignatory, Long>, JpaSpecificationExecutor<CertificateSignatory> {
}
