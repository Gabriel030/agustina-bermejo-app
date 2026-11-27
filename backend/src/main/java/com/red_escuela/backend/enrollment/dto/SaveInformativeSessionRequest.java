package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.InformativeSessionModality;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SaveInformativeSessionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDateTime scheduledAt,
        @NotNull InformativeSessionModality modality,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 500) String meetingUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 255) String location,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Min(1) Integer capacity,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long relatedProgramId) {
}
