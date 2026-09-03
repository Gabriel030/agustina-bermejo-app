package com.red_escuela.backend.dto;

// studentId: no hay un campo de legajo propio todavía (decisión con el usuario, Épica 2 Fase 1
// - Alumno · Inicio) — el frontend lo usa como "Legajo N°" hasta que exista uno real.
public record PaymentStandingResponse(Long studentId, String standing) {
}
