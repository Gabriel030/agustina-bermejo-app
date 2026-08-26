package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStaffRequest(
        @NotBlank String nombre, @NotBlank String apellido, @NotBlank @Email String email,
        @NotBlank String dni, String street, String number, String floor, String city,
        String state, String postalCode, String country,
        @NotBlank String role) {
}
