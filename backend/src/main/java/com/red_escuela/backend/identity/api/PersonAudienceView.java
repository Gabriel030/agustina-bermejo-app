package com.red_escuela.backend.identity.api;

public record PersonAudienceView(Long personId, String fullName, String firstName, String lastName, String email,
                                 boolean emailVerified, String phoneNumber) {
}
