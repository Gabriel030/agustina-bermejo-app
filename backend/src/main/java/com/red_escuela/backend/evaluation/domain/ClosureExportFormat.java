package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.evaluation.exception.InvalidClosureExportFormatException;

import java.util.Arrays;

public enum ClosureExportFormat {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;

    ClosureExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static ClosureExportFormat fromParameter(String value) {
        return Arrays.stream(values())
                .filter(format -> format.extension.equalsIgnoreCase(value == null ? "" : value.trim()))
                .findFirst()
                .orElseThrow(() -> new InvalidClosureExportFormatException(value));
    }
}
