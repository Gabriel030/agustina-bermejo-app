package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;

import com.red_escuela.backend.shared.domain.DocumentType;

import java.util.List;

public record PersonResponse(
        Long id, String legajo, boolean hasAvatar, String nombre, String apellido, String sexo,
        String email, String telefono, String dni, DocumentType documentType,
        @Schema(nullable = true) String street, @Schema(nullable = true) String number, @Schema(nullable = true) String floor, @Schema(nullable = true) String city, @Schema(nullable = true) String state,
        @Schema(nullable = true) String postalCode, @Schema(nullable = true) String country, List<String> roles) {

    public static PersonResponse from(Person person) {
        var address = person.getAddress();
        return new PersonResponse(
                person.getId(),
                person.getFile(),
                person.getAvatarPath() != null,
                person.getName(),
                person.getLastname(),
                person.getSex(),
                person.emailValue(),
                person.getPhoneNumber(),
                person.getDni(),
                person.getDocumentType(),
                address == null ? null : address.getStreet(),
                address == null ? null : address.getNumber(),
                address == null ? null : address.getFloor(),
                address == null ? null : address.getCity(),
                address == null ? null : address.getState(),
                address == null ? null : address.getPostalCode(),
                address == null ? null : address.getCountry(),
                person.getRoles().stream().map(Role::getName).toList());
    }
}
