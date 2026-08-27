package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.GraduationRequest;

import java.time.LocalDateTime;

public record GraduationRequestResponse(
        Long id, Long programEnrollmentId, String status,
        LocalDateTime requestedAt, LocalDateTime decidedAt, String rejectionReason) {

    public static GraduationRequestResponse from(GraduationRequest request) {
        return new GraduationRequestResponse(
                request.getId(), request.getProgramEnrollment().getId(), request.getStatus().name(),
                request.getRequestedAt(), request.getDecidedAt(), request.getRejectionReason());
    }
}
