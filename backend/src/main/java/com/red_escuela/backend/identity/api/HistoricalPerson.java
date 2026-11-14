package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;

public record HistoricalPerson(
        Long institutionId,
        HistoricalPersonRole role,
        DocumentType documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String email,
        String sex,
        LocalDate birthDate,
        String phone,
        String nationality,
        String street,
        String city,
        String state,
        String postalCode,
        LocalDate registeredOn,
        boolean active,
        LocalDate withdrawnOn,
        GuardianContact guardian,
        EmergencyContact emergencyContact) {
}
