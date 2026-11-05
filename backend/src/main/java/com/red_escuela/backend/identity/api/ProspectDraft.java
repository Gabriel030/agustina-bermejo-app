package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.shared.domain.DocumentType;

public record ProspectDraft(
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
