package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateExamBoardRequest(
        Long subjectId, Long courseSubjectId, @NotNull Long termId,
        @NotNull LocalDateTime examDate, LocalDateTime registrationStart, LocalDateTime registrationEnd,
        String classroom) {
}
