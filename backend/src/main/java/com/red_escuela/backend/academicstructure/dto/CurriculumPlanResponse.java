package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record CurriculumPlanResponse(
        Long id,
        Long programId,
        Integer versionNumber,
        String name,
        CurriculumPlanStatus status,
        LocalDate validFrom,
        @Schema(nullable = true) LocalDate validUntil,
        @Schema(nullable = true) LocalDate graduationDeadline,
        @Schema(nullable = true) Integer totalYears,
        Integer totalCreditsRequired,
        @Schema(nullable = true) Integer totalHoursRequired) {

    public static CurriculumPlanResponse from(CurriculumPlan plan) {
        return new CurriculumPlanResponse(plan.getId(), plan.getProgram().getId(), plan.getVersionNumber(),
                plan.getName(), plan.getStatus(), plan.getValidFrom(), plan.getValidUntil(),
                plan.getGraduationDeadline(), plan.getTotalYears(), plan.getTotalCreditsRequired(),
                plan.getTotalHoursRequired());
    }
}
