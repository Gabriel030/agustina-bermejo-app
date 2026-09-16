package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceChangeRequestResponse(
        Long id, Long attendanceId, String requestedByName, String subjectName, String sectionName,
        LocalDate classDate, String studentName, String previousStatus, String requestedStatus,
        String reason, String status, LocalDateTime createdAt) {

    public static AttendanceChangeRequestResponse from(
            AttendanceChangeRequest request, String requestedByName, String subjectName, String sectionName,
            LocalDate classDate, String studentName) {
        return new AttendanceChangeRequestResponse(
                request.getId(), request.getAttendance().getId(), requestedByName, subjectName, sectionName,
                classDate, studentName, request.getPreviousStatus().name(), request.getRequestedStatus().name(),
                request.getReason(), request.getStatus().name(), request.getCreatedAt());
    }
}
