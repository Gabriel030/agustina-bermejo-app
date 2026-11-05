package com.red_escuela.backend.attendance.dto;

public record ExportedAttendanceFile(byte[] content, String filename, String contentType) {
}
