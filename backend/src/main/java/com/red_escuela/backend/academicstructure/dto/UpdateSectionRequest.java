package com.red_escuela.backend.academicstructure.dto;

public record UpdateSectionRequest(String name, String schedule, Integer capacity, Long evaluationSchemeId) {
}
