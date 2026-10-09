package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.DocumentType;

public record SaveProspectDraftRequest(
        String dni,
        DocumentType documentType,
        String street,
        String number,
        String floor,
        String city,
        String state,
        String postalCode,
        String country) {
}
