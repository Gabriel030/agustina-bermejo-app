package com.red_escuela.backend.graduation.certificate.signatory.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

public record CertificateSignatoryDetailResponse(
        Long id,
        String name,
        String role,
        boolean active,
        boolean hasSignature,
        @Nullable Long signatureImageId,
        List<SignatoryDocumentType> allowedDocumentTypes,
        List<SignatoryTemplateUsageResponse> usedInTemplates,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
