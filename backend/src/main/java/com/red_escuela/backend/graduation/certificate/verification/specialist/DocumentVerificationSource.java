package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import java.time.LocalDateTime;
import java.util.Optional;

public interface DocumentVerificationSource {

    CertificateDocumentType type();

    Optional<PublicDocumentVerificationResponse> find(Long sourceId, LocalDateTime now);
}
