package com.red_escuela.backend.attendance.dto;

public record AttendanceJustificationAttachment(String fileName, String contentType, byte[] content) {
}
