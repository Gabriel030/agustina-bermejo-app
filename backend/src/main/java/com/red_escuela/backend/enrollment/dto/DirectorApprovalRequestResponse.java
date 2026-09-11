package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;

import java.time.LocalDateTime;

public record DirectorApprovalRequestResponse(
        Long id, String type, Long programEnrollmentId, String studentName, String programName,
        Long subjectId, String subjectName, String justification,
        String status, LocalDateTime requestedAt, LocalDateTime decidedAt, String decisionNotes) {

    public static DirectorApprovalRequestResponse from(DirectorApprovalRequest request) {
        var programEnrollment = request.getProgramEnrollment();
        var person = programEnrollment.getStudent().getPerson();
        var subject = request.getSubject();
        return new DirectorApprovalRequestResponse(
                request.getId(), request.getType().name(), programEnrollment.getId(),
                person.getName() + " " + person.getLastname(), programEnrollment.getProgram().getName(),
                subject != null ? subject.getId() : null, subject != null ? subject.getName() : null,
                request.getJustification(), request.getStatus().name(),
                request.getRequestedAt(), request.getDecidedAt(), request.getDecisionNotes());
    }
}
