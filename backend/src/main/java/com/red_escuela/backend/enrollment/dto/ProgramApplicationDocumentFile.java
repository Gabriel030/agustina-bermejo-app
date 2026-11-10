package com.red_escuela.backend.enrollment.dto;

public record ProgramApplicationDocumentFile(String fileName, String contentType, byte[] content) {
}
