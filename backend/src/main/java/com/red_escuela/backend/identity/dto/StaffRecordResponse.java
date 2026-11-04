package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.LocalDateTime;

public record StaffRecordResponse(
        Long id,
        String nombre,
        String apellido,
        String dni,
        DocumentType documentType,
        String username,
        String email,
        String telefono,
        String role,
        String status,
        AddressResponse domicilio,
        Boolean emailVerified,
        AuthProvider authProvider,
        LocalDateTime lockedUntil,
        Integer failedLoginAttempts
) {
    public static StaffRecordResponse from(Person person, String role, boolean includeFailedLoginAttempts) {
        return new StaffRecordResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                person.getDocumentType(),
                person.getUsername(),
                person.emailValue(),
                person.getPhoneNumber(),
                role,
                person.getStatus() == null ? null : person.getStatus().name(),
                person.getAddress() == null ? null : AddressResponse.from(person.getAddress()),
                person.getEmailVerified(),
                person.getAuthProvider(),
                activeLockedUntil(person),
                includeFailedLoginAttempts ? person.getFailedLoginAttempts() : null
        );
    }

    private static LocalDateTime activeLockedUntil(Person person) {
        LocalDateTime lockedUntil = person.getLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(ApplicationTime.now()) ? lockedUntil : null;
    }
}
