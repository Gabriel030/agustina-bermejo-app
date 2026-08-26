package com.red_escuela.backend.entity;

// No persistido: solo el resultado calculado on-demand de InvoiceService.calcularEstadoDePago
// (decisión 17 de la spec) — punto de extensión a futuro para una validación de inscripción,
// sin ningún caller de bloqueo todavía.
public enum PaymentStanding {
    AL_DIA,
    MOROSO,
    SIN_INFORMACION
}
