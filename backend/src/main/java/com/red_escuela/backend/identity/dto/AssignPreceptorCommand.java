package com.red_escuela.backend.identity.dto;

public record AssignPreceptorCommand(Long personId, Long subjectSectionId, Long courseSectionId, Long actorId) {
}
