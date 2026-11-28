package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateHolidayRequest(@NotNull LocalDate date, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String name,
                                   @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) HolidayType type,
                                   @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String description,
                                   @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) HolidayScope scope) implements SelfValidatingRequest {
    public CreateHolidayRequest(LocalDate date, String name, HolidayType type, String description) {
        this(date, name, type, description, null);
    }

    public CreateHolidayRequest {
        RequestLimits.requireText("name", name);
        RequestLimits.requireValue("type", type);
        RequestLimits.requireMaxLength("name", name, 255);
        RequestLimits.requireMaxLength("description", description, 255);
    }
}
