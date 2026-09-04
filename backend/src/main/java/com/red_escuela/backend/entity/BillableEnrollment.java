package com.red_escuela.backend.entity;

import java.time.LocalDateTime;

// Interfaz de dominio pura (no un patrón GoF/DDD con nombre propio): resuelve tratar
// CourseEnrollment/ProgramEnrollment (y Subscription desde Fase 6.10) de forma polimórfica en
// InvoiceGenerationService/InvoiceOverdueSweepService/EnrollmentBillingComplianceSweepService,
// sin forzar una superclase JPA invasiva entre entidades ya en producción.
public interface BillableEnrollment {
    Student getStudent();

    EnrollmentBillingStanding getBillingStanding();

    void setBillingStanding(EnrollmentBillingStanding standing);

    LocalDateTime getBillingStandingUpdatedAt();

    void setBillingStandingUpdatedAt(LocalDateTime updatedAt);
}
