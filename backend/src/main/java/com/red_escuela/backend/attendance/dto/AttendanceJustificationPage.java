package com.red_escuela.backend.attendance.dto;

import java.util.List;

public record AttendanceJustificationPage(List<AttendanceJustificationResponse> items, long totalElements) {
}
