package com.red_escuela.backend.identity.usecase.auth;

public record RegisterWithGoogleCommand(String idToken, Long institutionId) {
}
