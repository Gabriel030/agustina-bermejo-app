package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceSectionPreceptorsRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<@NotNull Long> personIds) {
}
