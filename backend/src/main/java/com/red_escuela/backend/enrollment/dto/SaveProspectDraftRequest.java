package com.red_escuela.backend.enrollment.dto;

public record SaveProspectDraftRequest(
        String dni,
        String street,
        String number,
        String floor,
        String city,
        String state,
        String postalCode,
        String country) {
}
