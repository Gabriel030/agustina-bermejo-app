package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.shared.domain.DocumentType;

public record FirstDirectorProvisioningCommand(
        String institutionName,
        String firstName,
        String lastName,
        String email,
        DocumentType documentType,
        String documentNumber,
        String reason,
        String idempotencyKey) {
}
