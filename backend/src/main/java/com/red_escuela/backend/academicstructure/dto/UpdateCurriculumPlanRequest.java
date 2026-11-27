package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;

import java.time.LocalDate;

public record UpdateCurriculumPlanRequest(
        String name, LocalDate validFrom, LocalDate graduationDeadline,
        Integer totalYears, Integer totalCreditsRequired, Integer totalHoursRequired) implements SelfValidatingRequest {
    public UpdateCurriculumPlanRequest {
        RequestLimits.requireMaxLength("name", name, RequestLimits.SHORT_TEXT);
        RequestLimits.requireAtMost("totalYears", totalYears, 20);
        RequestLimits.requireAtMost("totalCreditsRequired", totalCreditsRequired, 10000);
        RequestLimits.requireAtMost("totalHoursRequired", totalHoursRequired, 100000);
    }

    public boolean changesTotals() {
        return totalYears != null || totalCreditsRequired != null || totalHoursRequired != null;
    }
}
