package com.red_escuela.backend.entity;

// OVERDUE/PAID/CANCELLED are used starting Fase 6.2/6.3, but the full enum ships from the start.
public enum InvoiceStatus {
    PENDING, OVERDUE, PAID, WAIVED, CANCELLED
}
