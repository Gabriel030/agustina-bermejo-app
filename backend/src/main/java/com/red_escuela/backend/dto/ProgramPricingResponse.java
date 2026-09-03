package com.red_escuela.backend.dto;

import java.math.BigDecimal;

// Consulta pública (cualquier autenticado) para que el alumno vea cuánto tiene que
// pagar ANTES de transferir — separado del CRUD de FeeSchedule (Director-only).
// Cualquier monto null significa que el Director todavía no configuró ese precio.
public record ProgramPricingResponse(
        Long programId, BigDecimal matriculaCohorte1, BigDecimal matriculaCohorte2, BigDecimal cuotaMensual) {
}
