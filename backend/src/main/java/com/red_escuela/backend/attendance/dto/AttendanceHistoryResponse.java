package com.red_escuela.backend.attendance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AttendanceHistoryResponse(List<LocalDate> classDates, List<StudentHistoryRow> rows) {

    public record HistoryCell(Long attendanceId, String status) {
    }

    public record StudentHistoryRow(
            Long studentId, String studentName, Map<LocalDate, HistoryCell> cellsByDate, Double attendancePercentage) {
    }
}
