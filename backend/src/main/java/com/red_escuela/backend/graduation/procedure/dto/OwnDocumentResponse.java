package com.red_escuela.backend.graduation.procedure.dto;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import java.time.LocalDateTime;

public record OwnDocumentResponse(CertificateDocumentType documentType, Long id, String code, DocumentValidityStatus status, LocalDateTime issuedAt, LocalDateTime expiresAt, String verificationUrl) {
}
