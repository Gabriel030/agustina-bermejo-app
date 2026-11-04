package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCycleSectionRequest(
        @NotBlank String name,
        Long subjectId,
        Long courseSubjectId,
        @NotNull @Positive Integer capacity,
        List<ScheduleSlotRequest> slots,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Percentage maxAbsencePercent,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) SectionShift shift,
        @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String description
) {

    public CreateCycleSectionRequest(String name, Long subjectId, Long courseSubjectId, Integer capacity, List<ScheduleSlotRequest> slots, Percentage maxAbsencePercent) {
        this(name, subjectId, courseSubjectId, capacity, slots, maxAbsencePercent, null, null);
    }


    public CreateCycleSectionRequest(String name, Long subjectId, Long courseSubjectId, Integer capacity, List<ScheduleSlotRequest> slots) {
        this(name, subjectId, courseSubjectId, capacity, slots, null, null, null);
    }
}
