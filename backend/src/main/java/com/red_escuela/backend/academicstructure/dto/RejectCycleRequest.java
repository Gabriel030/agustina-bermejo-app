package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectCycleRequest(@NotBlank String reason) {}
