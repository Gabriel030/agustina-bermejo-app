package com.red_escuela.backend.graduation.procedure.dto;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ApprovedProcedureResponse(CertificateDocumentType documentType, Long id, String title, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String detail, LocalDateTime issuedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime expiresAt, DocumentValidityStatus status, DocumentOrigin origin, boolean verifiable) {
}
