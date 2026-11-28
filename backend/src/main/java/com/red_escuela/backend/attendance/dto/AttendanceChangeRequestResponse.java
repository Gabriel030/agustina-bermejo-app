package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.domain.AttendanceDecisionRevertWindow;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.shared.time.ApplicationTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceChangeRequestResponse(
        Long id, Long attendanceId, String requestedByName, String subjectName, String sectionName,
        LocalDate classDate, String studentName, String previousStatus, String requestedStatus,
        String reason, String status, LocalDateTime createdAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String rejectionReason,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime decidedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String decidedByName,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate revertibleUntil,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) boolean revertible,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String revertedByName,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime revertedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String revertReason) {

    public static AttendanceChangeRequestResponse from(
            AttendanceChangeRequest request, String requestedByName, String subjectName, String sectionName,
            LocalDate classDate, String studentName, String decidedByName, String revertedByName) {
        boolean decided = request.getStatus() != AttendanceChangeRequestStatus.PENDING;
        LocalDateTime decidedAt = decided ? request.getApprovedAt() : null;
        return new AttendanceChangeRequestResponse(
                request.getId(), request.getAttendance().getId(), requestedByName, subjectName, sectionName,
                classDate, studentName, request.getPreviousStatus().name(), request.getRequestedStatus().name(),
                request.getReason(), request.getStatus().name(), request.getCreatedAt(), request.getRejectionReason(),
                decidedAt, decidedByName, AttendanceDecisionRevertWindow.lastRevertibleDay(decidedAt),
                AttendanceDecisionRevertWindow.isOpen(decidedAt, ApplicationTime.today()),
                revertedByName, request.getRevertedAt(), request.getRevertReason());
    }
}
