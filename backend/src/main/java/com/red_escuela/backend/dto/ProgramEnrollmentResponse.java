package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ProgramEnrollment;

import java.time.LocalDateTime;

public record ProgramEnrollmentResponse(
        Long id, Long studentId, Long programId, String programName, String status,
        LocalDateTime enrollmentDate, LocalDateTime completionDate) {

    public static ProgramEnrollmentResponse from(ProgramEnrollment programEnrollment) {
        return new ProgramEnrollmentResponse(
                programEnrollment.getId(),
                programEnrollment.getStudent().getId(),
                programEnrollment.getProgram().getId(),
                programEnrollment.getProgram().getName(),
                programEnrollment.getStatus() == null ? null : programEnrollment.getStatus().name(),
                programEnrollment.getEnrollmentDate(),
                programEnrollment.getCompletionDate());
    }
}
