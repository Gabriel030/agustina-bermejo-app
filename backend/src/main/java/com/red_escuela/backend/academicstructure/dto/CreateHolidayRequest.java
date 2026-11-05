package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateHolidayRequest(@NotNull LocalDate date, @NotBlank String name, @NotNull HolidayType type,
                                   String description) implements SelfValidatingRequest {
    public CreateHolidayRequest {
        RequestLimits.requireMaxLength("name", name, 255);
        RequestLimits.requireMaxLength("description", description, 255);
    }
}
