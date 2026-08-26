package com.red_escuela.backend.dto;

import java.util.List;

public record ProgramProgressResponse(
        Long programEnrollmentId, Integer creditosAcumulados, Integer creditosRequeridos,
        List<String> obligatoriasPendientes, List<ElectiveGroupProgressResponse> gruposElectivos,
        boolean elegibleParaCompletar) {
}
