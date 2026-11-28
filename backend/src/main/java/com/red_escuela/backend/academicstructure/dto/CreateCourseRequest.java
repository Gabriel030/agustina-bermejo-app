package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.lang.Nullable;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.NonNegativeMoney;

import java.time.LocalDateTime;
import java.util.List;

public record CreateCourseRequest(
        @NotBlank String name, @NotNull Long termId, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String schedule,
        @NotNull @Positive Integer capacity, @NonNegativeMoney Money price, @NotNull @Positive Integer durationMonths,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) TeachingMode teachingMode,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime registrationStart, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime registrationEnd, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Currency currency,
        @Nullable @Positive(message = "La carga horaria debe ser mayor a cero") Integer durationHours,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<Long> responsiblePreceptorIds) {

    public CreateCourseRequest(String name, Long termId, String schedule, Integer capacity, Money price,
                               Integer durationMonths, TeachingMode teachingMode,
                               LocalDateTime registrationStart, LocalDateTime registrationEnd, Currency currency,
                               Integer durationHours) {
        this(name, termId, schedule, capacity, price, durationMonths, teachingMode, registrationStart, registrationEnd,
                currency, durationHours, null);
    }

    public CreateCourseRequest(String name, Long termId, String schedule, Integer capacity, Money price,
                               Integer durationMonths, TeachingMode teachingMode,
                               LocalDateTime registrationStart, LocalDateTime registrationEnd, Currency currency) {
        this(name, termId, schedule, capacity, price, durationMonths, teachingMode, registrationStart, registrationEnd,
                currency, null);
    }

    public CreateCourseRequest(String name, Long termId, String schedule, Integer capacity, Money price,
                               Integer durationMonths, TeachingMode teachingMode,
                               LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        this(name, termId, schedule, capacity, price, durationMonths, teachingMode, registrationStart, registrationEnd, null, null);
    }
}
