package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;

import java.time.LocalDateTime;

public record AcademicOfferingCycleResponse(
        Long id,
        String status,
        Long termId,
        String termName,
        @Schema(nullable = true) Long programId,
        @Schema(nullable = true) String programName,
        @Schema(nullable = true) Long courseId,
        @Schema(nullable = true) String courseName,
        String createdByName,
        LocalDateTime createdAt,
        @Schema(nullable = true) String decidedByName,
        @Schema(nullable = true) LocalDateTime decidedAt,
        @Schema(nullable = true) String rejectionReason,
        @Schema(nullable = true) String submittedByName,
        @Schema(nullable = true) LocalDateTime submittedAt,
        long sectionCount
) {
    public static AcademicOfferingCycleResponse from(AcademicOfferingCycle c, long sectionCount) {
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
                c.getRejectionReason(),
                c.getSubmittedBy() != null ? c.getSubmittedBy().getName() + " " + c.getSubmittedBy().getLastname() : null,
                c.getSubmittedAt(),
                sectionCount
        );
    }
}
