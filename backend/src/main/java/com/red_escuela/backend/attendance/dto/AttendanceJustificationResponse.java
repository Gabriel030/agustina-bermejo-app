package com.red_escuela.backend.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceJustificationResponse(
        Long attendanceId, String studentName, String subjectName, String sectionName, LocalDate classDate,
        String reason, String attachmentUrl, String status, String rejectionReason, LocalDateTime decidedAt) {
}
