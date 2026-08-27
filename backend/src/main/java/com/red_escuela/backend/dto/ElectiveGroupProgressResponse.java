package com.red_escuela.backend.dto;

public record ElectiveGroupProgressResponse(
        Long electiveGroupId, String electiveGroupName,
        Integer cantidadAprobada, Integer minRequired, Integer maxAllowed, boolean cumplido) {
}
