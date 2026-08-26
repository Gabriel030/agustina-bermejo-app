package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Email;

public record UpdateStaffRequest(
        @Email String email, String telefono, String street, String number, String floor,
        String city, String state, String postalCode, String country) {
}
