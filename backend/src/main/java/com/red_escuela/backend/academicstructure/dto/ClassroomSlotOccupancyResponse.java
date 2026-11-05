package com.red_escuela.backend.academicstructure.dto;

import org.springframework.lang.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ClassroomSlotOccupancyResponse(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        OccupyingSection section,
        OccupyingTerm term,
        boolean overlapsInDates
) {
    public enum SectionKind { PROGRAM, COURSE }

    public record OccupyingSection(Long id, @Nullable String code, String name, String sectionName, SectionKind kind) {
    }

    public record OccupyingTerm(Long id, String name, LocalDateTime startDate, LocalDateTime endDate) {
    }
}
