package com.red_escuela.backend.attendance.dto;

import jakarta.validation.constraints.NotBlank;

public record JustificarAsistenciaRequest(@NotBlank String reason, String attachmentUrl) {
}
