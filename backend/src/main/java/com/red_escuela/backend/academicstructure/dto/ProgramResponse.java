package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Program;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProgramResponse {
    private final Long id;
    private final String name;
    private final Integer totalYears;
    private final BigDecimal price;
    private final BigDecimal enrollmentFeeAmount;
    private final String status;
    private final Integer totalCreditsRequired;
    private final Integer totalHoursRequired;
    private final boolean visibleInCatalog;
    private final String teachingMode;
    private final String level;
    private final String graduationProfile;
    private final String admissionRequirements;

    public static ProgramResponse from(Program program) {
        return ProgramResponse.builder()
                .id(program.getId())
                .name(program.getName())
                .totalYears(program.getTotalYears())
                .price(program.getPrice())
                .enrollmentFeeAmount(program.getEnrollmentFeeAmount())
                .status(program.getStatus() == null ? null : program.getStatus().name())
                .totalCreditsRequired(program.getTotalCreditsRequired())
                .totalHoursRequired(program.getTotalHoursRequired())
                .visibleInCatalog(program.isVisibleInCatalog())
                .teachingMode(program.getTeachingMode() == null ? null : program.getTeachingMode().name())
                .level(program.getLevel() == null ? null : program.getLevel().name())
                .graduationProfile(program.getGraduationProfile())
                .admissionRequirements(program.getAdmissionRequirements())
                .build();
    }
}
