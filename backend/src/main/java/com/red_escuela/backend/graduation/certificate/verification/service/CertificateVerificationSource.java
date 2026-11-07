package com.red_escuela.backend.graduation.certificate.verification.service;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CertificateVerificationSource {

    Optional<PublicCertificateView> findByCode(CertificateCode code, LocalDateTime now);
}
