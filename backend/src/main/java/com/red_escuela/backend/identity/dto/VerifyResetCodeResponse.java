package com.red_escuela.backend.identity.dto;

public record VerifyResetCodeResponse(String resetToken) {

    public static VerifyResetCodeResponse from(String resetToken) {
        return new VerifyResetCodeResponse(resetToken);
    }
}
