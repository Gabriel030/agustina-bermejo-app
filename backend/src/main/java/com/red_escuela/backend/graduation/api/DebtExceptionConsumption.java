package com.red_escuela.backend.graduation.api;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import java.util.Optional;

public interface DebtExceptionConsumption {

    Optional<Long> consumeActive(Long studentId, CertificateDocumentType documentType, Long registrationId);
}
