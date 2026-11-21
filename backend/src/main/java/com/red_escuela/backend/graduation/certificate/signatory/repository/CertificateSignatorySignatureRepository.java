package com.red_escuela.backend.graduation.certificate.signatory.repository;

import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatorySignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificateSignatorySignatureRepository extends JpaRepository<CertificateSignatorySignature, Long> {

    Optional<CertificateSignatorySignature> findBySignatoryId(Long signatoryId);

    List<CertificateSignatorySignature> findBySignatoryIdIn(Collection<Long> signatoryIds);
}
