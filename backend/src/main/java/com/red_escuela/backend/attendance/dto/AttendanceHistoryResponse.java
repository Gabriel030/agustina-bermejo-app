package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.shared.domain.Percentage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AttendanceHistoryResponse(List<LocalDate> classDates, List<StudentHistoryRow> rows) {

    public record HistoryCell(Long attendanceId, String status, LocalDate justificationDeadline, boolean justifiable,
                              JustificationRejection rejection) {
    }

    public record JustificationRejection(String reason, LocalDateTime rejectedAt) {
    }

    public record StudentHistoryRow(
            Long studentId, String studentName, Map<LocalDate, HistoryCell> cellsByDate, Percentage attendancePercent) {
    }
}
