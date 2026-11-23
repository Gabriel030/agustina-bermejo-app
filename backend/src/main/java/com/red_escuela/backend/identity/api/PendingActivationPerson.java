package com.red_escuela.backend.identity.api;

public record PendingActivationPerson(Long personId, String email, String displayName) {
}
