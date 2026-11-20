package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;

import java.time.LocalDateTime;

public record WaitlistCandidate(WaitlistKind kind, Long enrollmentId, Long studentId, String fullName, String documentNumber,
                                LocalDateTime joinedAt, Long programEnrollmentId, Long subjectId, Long sectionId, Long termId, LocalDateTime prioritizedAt) {
}
