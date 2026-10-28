package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;

public record EnrollmentClosedWhileSuspendedEvent(EnrollmentKind kind, Long enrollmentId, LocalDate businessDate) {
}
