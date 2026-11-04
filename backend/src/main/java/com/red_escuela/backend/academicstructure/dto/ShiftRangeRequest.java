package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ShiftRangeRequest(
        @NotNull SectionShift shift,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {}
