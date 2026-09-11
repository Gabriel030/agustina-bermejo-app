package com.red_escuela.backend.enrollment.dto;

public record ElectiveGroupProgressResponse(
        Long electiveGroupId, String electiveGroupName,
        Integer cantidadAprobada, Integer minRequired, Integer maxAllowed, boolean cumplido) {
}
