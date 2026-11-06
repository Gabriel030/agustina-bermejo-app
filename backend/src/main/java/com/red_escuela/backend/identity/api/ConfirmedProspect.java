package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;

public record ConfirmedProspect(
        String dni,
        DocumentType documentType,
        String street,
        String number,
        String floor,
        String city,
        String state,
        String postalCode,
        String country,
        LocalDate birthDate,
        String sex,
        String nationality,
        GuardianContact guardian,
        EmergencyContact emergencyContact) {
}
