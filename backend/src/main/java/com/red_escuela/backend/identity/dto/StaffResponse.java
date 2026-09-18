package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Person;

public record StaffResponse(
        Long id, String nombre, String apellido, String dni,
        String username, String email, String telefono, String role) {
    public static StaffResponse from(Person person, String role) {
        return new StaffResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                person.getUsername(),
                person.getEmail(),
                person.getPhoneNumber(),
                role);
    }
}
