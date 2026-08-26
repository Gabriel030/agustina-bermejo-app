package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.DirectorApprovalRequest;

import java.time.LocalDateTime;

public record DirectorApprovalRequestResponse(
        Long id, String type, Long programEnrollmentId, Long subjectId, String justification,
        String status, LocalDateTime requestedAt, LocalDateTime decidedAt, String decisionNotes) {

    public static DirectorApprovalRequestResponse from(DirectorApprovalRequest request) {
        return new DirectorApprovalRequestResponse(
                request.getId(), request.getType().name(), request.getProgramEnrollment().getId(),
                request.getSubject() != null ? request.getSubject().getId() : null,
                request.getJustification(), request.getStatus().name(),
                request.getRequestedAt(), request.getDecidedAt(), request.getDecisionNotes());
    }
}
