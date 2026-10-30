package com.red_escuela.backend.graduation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.graduation.entity.GraduationRequest;

import java.time.LocalDateTime;

public record GraduationRequestResponse(
        Long id, Long programEnrollmentId, Long studentId, String studentName, String programName, String status,
        LocalDateTime requestedAt, @Schema(nullable = true) LocalDateTime decidedAt, @Schema(nullable = true) String rejectionReason) {

    public static GraduationRequestResponse from(GraduationRequest request) {
        var programEnrollment = request.getProgramEnrollment();
        var student = programEnrollment.getStudent();
        var person = student.getPerson();
        return new GraduationRequestResponse(
                request.getId(), programEnrollment.getId(), student.getId(),
                person.getName() + " " + person.getLastname(), programEnrollment.getProgram().getName(),
                request.getStatus().name(), request.getRequestedAt(), request.getDecidedAt(), request.getRejectionReason());
    }
}
