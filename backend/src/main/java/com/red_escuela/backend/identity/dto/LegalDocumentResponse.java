package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.LegalDocumentType;

public record LegalDocumentResponse(LegalDocumentType type, String version, String path) {
}
