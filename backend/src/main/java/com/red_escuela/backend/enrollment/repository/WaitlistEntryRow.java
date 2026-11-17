package com.red_escuela.backend.enrollment.repository;

import java.time.LocalDateTime;

public interface WaitlistEntryRow {
    Long getEnrollmentId();

    Long getStudentId();

    String getFullName();

    String getDocumentNumber();

    LocalDateTime getJoinedAt();
}
