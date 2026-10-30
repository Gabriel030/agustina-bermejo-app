package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;

import java.time.LocalDateTime;

public record AcademicOfferingCycleResponse(
        Long id,
        String status,
        Long termId,
        String termName,
        Long programId,
        String programName,
        Long courseId,
        String courseName,
        String createdByName,
        LocalDateTime createdAt,
        String decidedByName,
        @Schema(nullable = true) LocalDateTime decidedAt,
        @Schema(nullable = true) String rejectionReason
) {
    public static AcademicOfferingCycleResponse from(AcademicOfferingCycle c) {
        return new AcademicOfferingCycleResponse(
                c.getId(),
                c.getStatus().name(),
                c.getTerm().getId(),
                c.getTerm().getDescription(),
                c.getProgram() != null ? c.getProgram().getId() : null,
                c.getProgram() != null ? c.getProgram().getName() : null,
                c.getCourse() != null ? c.getCourse().getId() : null,
                c.getCourse() != null ? c.getCourse().getName() : null,
                c.getCreatedBy().getName() + " " + c.getCreatedBy().getLastname(),
                c.getCreatedAt(),
                c.getDecidedBy() != null ? c.getDecidedBy().getName() + " " + c.getDecidedBy().getLastname() : null,
                c.getDecidedAt(),
                c.getRejectionReason()
        );
    }
}
