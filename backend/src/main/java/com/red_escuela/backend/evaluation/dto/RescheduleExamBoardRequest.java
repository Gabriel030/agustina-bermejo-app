package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record RescheduleExamBoardRequest(
        @NotNull LocalDateTime examDate, LocalDateTime registrationStart, LocalDateTime registrationEnd,
        @Size(max = 255) String classroom) {
}
