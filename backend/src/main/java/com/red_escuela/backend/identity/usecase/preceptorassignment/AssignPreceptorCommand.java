package com.red_escuela.backend.identity.usecase.preceptorassignment;

public record AssignPreceptorCommand(Long personId, Long subjectSectionId, Long courseSectionId, Long actorId) {
}
