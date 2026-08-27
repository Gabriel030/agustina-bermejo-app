package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;

import java.util.List;

public record PersonResponse(
        Long id, String nombre, String apellido, String email, String telefono,
        String street, String number, String floor, String city, String state,
        String postalCode, String country, List<String> roles) {

    public static PersonResponse from(Person person) {
        var address = person.getAddress();
        return new PersonResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getEmail(),
                person.getPhoneNumber(),
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
