package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.identity.entity.Person;

public record StaffResponse(
        Long id, String nombre, String apellido, String dni,
        String username, String email, @Schema(nullable = true) String telefono, String role, String status) {
    public static StaffResponse from(Person person, String role) {
        return new StaffResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                person.getUsername(),
                person.emailValue(),
                person.getPhoneNumber(),
                role,
                person.getStatus() == null ? null : person.getStatus().name());
    }
}
