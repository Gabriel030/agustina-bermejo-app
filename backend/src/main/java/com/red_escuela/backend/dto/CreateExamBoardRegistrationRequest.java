package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

// XOR igual que la entidad ExamBoardRegistration: se informa una sola de las dos inscripciones
// (program o course), según de qué lado sea la cursada del alumno para esa materia.
public record CreateExamBoardRegistrationRequest(
        @NotNull Long examBoardId, Long programSectionEnrollmentId, Long courseSectionEnrollmentId) {
}
