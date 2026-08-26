package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Person;

/**
 * "id" es el id de Person, no de Instructor: PRECEPTOR no tiene entidad propia
 * (solo DOCENTE la tiene, vía Instructor), así que /staff/{id} identifica siempre
 * por Person para que ambos roles usen la misma clave de forma uniforme.
 */
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
