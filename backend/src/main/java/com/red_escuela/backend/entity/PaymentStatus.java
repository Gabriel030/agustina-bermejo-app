package com.red_escuela.backend.entity;

// PENDING_GATEWAY_CONFIRMATION solo se usa para pagos iniciados por Mercado Pago (Fase 6.9) — en
// Fase 6.2, todo pago manual (CASH/TRANSFER/CARD) nace directo en CONFIRMED o REJECTED.
public enum PaymentStatus {
    PENDING_GATEWAY_CONFIRMATION, CONFIRMED, REJECTED
}
