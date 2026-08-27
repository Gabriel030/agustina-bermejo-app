package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ProgramEnrollmentApplication;

import java.time.LocalDateTime;

public record ProgramEnrollmentApplicationResponse(
        Long id,
        Long studentId,
        Long programId,
        String programName,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        String rejectionReason) {

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application) {
        return new ProgramEnrollmentApplicationResponse(
                application.getId(),
                application.getStudent().getId(),
                application.getProgram().getId(),
                application.getProgram().getName(),
                application.getStatus().name(),
                application.getRequestedAt(),
                application.getDecidedAt(),
                application.getRejectionReason());
    }
}
