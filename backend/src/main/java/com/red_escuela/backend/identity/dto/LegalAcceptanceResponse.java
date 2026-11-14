package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.LegalDocumentType;

import java.time.LocalDateTime;

public record LegalAcceptanceResponse(Long id, Long personId, String personName, String personLastname,
                                      LegalDocumentType documentType, String version, LocalDateTime acceptedAt) {
}
