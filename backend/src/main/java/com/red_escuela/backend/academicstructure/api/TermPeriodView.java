package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDateTime;

@NamedInterface("api")
public record TermPeriodView(Long termId, LocalDateTime startDate, LocalDateTime endDate) {

    public boolean overlaps(TermPeriodView other) {
        if (termId.equals(other.termId)) {
            return true;
        }
        return !startDate.isAfter(other.endDate) && !other.startDate.isAfter(endDate);
    }
}
