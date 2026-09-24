package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Program;

import java.math.BigDecimal;

public record ProgramResponse(
        Long id, String name, Integer totalYears,
        BigDecimal price, BigDecimal enrollmentFeeAmount,
        String status, Integer totalCreditsRequired,
        Integer totalHoursRequired, boolean visibleInCatalog,
        String teachingMode, String level,
        String graduationProfile, String admissionRequirements) {

    public static ProgramResponse from(Program program) {
        return new ProgramResponse(
                program.getId(), program.getName(), program.getTotalYears(),
                program.getPrice(), program.getEnrollmentFeeAmount(),
                program.getStatus() == null ? null : program.getStatus().name(),
                program.getTotalCreditsRequired(),
                program.getTotalHoursRequired(), program.isVisibleInCatalog(),
                program.getTeachingMode() == null ? null : program.getTeachingMode().name(),
                program.getLevel() == null ? null : program.getLevel().name(),
                program.getGraduationProfile(), program.getAdmissionRequirements());
    }
}
