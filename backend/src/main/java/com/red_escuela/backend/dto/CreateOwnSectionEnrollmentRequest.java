package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

// Variante de CreateSectionEnrollmentRequest para /me/section-enrollments: al ser
// autoservicio del alumno, no hay {id} de ProgramEnrollment en la URL (decisión 12),
// pero decisión 6 exige igual que el alumno lo indique explícitamente — una Subject
// puede estar en el plan de más de una de sus carreras activas (decisión 1).
public record CreateOwnSectionEnrollmentRequest(@NotNull Long programEnrollmentId, @NotNull Long sectionId) {
}
