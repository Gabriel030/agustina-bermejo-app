package com.red_escuela.backend.attendance.dto;

import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceJustificationResponse(
        Long attendanceId, String studentName, String subjectName, String sectionName, LocalDate classDate,
        String reason, @Nullable String attachmentUrl, String status, @Nullable String rejectionReason,
        @Nullable LocalDateTime decidedAt) {
}
