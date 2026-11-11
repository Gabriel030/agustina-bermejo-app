package com.red_escuela.backend.evaluation.dto;

public record ClosureExportFile(byte[] content, String fileName, String contentType) {
}
