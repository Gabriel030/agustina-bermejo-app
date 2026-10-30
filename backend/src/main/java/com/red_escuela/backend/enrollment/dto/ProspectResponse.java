package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.identity.api.StudentOnboardingSummary;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;

public record ProspectResponse(
        Long id, Long personId, String nombre, String apellido, String dni,
        String username, String email, StudentOnboardingStatus onboardingStatus,
        String status, Boolean emailVerified) {

    public static ProspectResponse from(StudentOnboardingSummary summary) {
        return new ProspectResponse(
                summary.studentId(),
                summary.personId(),
                summary.firstName(),
                summary.lastName(),
                summary.dni(),
                summary.username(),
                summary.email(),
                summary.onboardingStatus(),
                summary.personStatus(),
                summary.emailVerified());
    }
}
