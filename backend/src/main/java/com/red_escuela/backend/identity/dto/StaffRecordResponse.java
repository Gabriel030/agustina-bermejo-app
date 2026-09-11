package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Person;

public record StaffRecordResponse(
        Long id,
        String nombre,
        String apellido,
        String dni,
        String username,
        String email,
        String telefono,
        String role,
        String status,
        AddressResponse domicilio
) {
    public static StaffRecordResponse from(Person person, String role) {
        return new StaffRecordResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                person.getUsername(),
                person.getEmail(),
                person.getPhoneNumber(),
                role,
                person.getStatus() == null ? null : person.getStatus().name(),
                person.getAddress() == null ? null : AddressResponse.from(person.getAddress())
        );
    }
}
