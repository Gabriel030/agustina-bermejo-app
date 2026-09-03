package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Email;

public record UpdateOwnProfileRequest(
        @Email String email, String telefono, String dni, String street, String number, String floor,
        String city, String state, String postalCode, String country) {
}
