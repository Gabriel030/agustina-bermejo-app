package com.red_escuela.backend.entity;

public enum EnrollmentStatus {
    ENROLLED,
    IN_PROGRESS,
    REGULAR,
    APPROVED,
    FAILED,
    WITHDRAWN,
    WAITLISTED,
    // Cupo de Course.capacity (no de una comisión puntual) agotado y no liberado antes del
    // corte configurado en Course.waitlistDeadlineOffsetDays — CourseEnrollmentWaitlistExpiryJob
    // la asigna, nunca un alumno la elige. Distinta de WITHDRAWN: esta es involuntaria y
    // dispara reembolso (ver TODO en CourseEnrollmentWaitlistExpiryJob).
    WAITLIST_EXPIRED
}
