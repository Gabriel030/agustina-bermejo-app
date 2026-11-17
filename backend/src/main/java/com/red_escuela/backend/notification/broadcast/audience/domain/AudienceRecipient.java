package com.red_escuela.backend.notification.broadcast.audience.domain;

public record AudienceRecipient(Long personId, String fullName, String email, boolean emailVerified, String phoneNumber) {
}
