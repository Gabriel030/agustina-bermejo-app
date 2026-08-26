package com.red_escuela.backend.dto;

public record UpdateSectionRequest(String name, String schedule, Integer capacity, Long evaluationSchemeId) {
}
