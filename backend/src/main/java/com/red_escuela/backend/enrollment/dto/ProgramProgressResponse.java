package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record ProgramProgressResponse(
        Long programEnrollmentId, Integer creditosAcumulados, Integer creditosRequeridos,
        List<String> obligatoriasPendientes, List<ElectiveGroupProgressResponse> gruposElectivos,
        boolean elegibleParaCompletar,
        @Deprecated @Schema(deprecated = true, description = "Usar hasActiveCoursework") boolean esRegular,
        BigDecimal promedioGeneral,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) boolean hasActiveCoursework) {
}
