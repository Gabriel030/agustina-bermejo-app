package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;

import java.time.LocalDateTime;

public record StudentDocumentResponse(Long id, DocumentType type, LocalDateTime uploadedAt) {

    public static StudentDocumentResponse from(StudentDocument document) {
        return new StudentDocumentResponse(document.getId(), document.getType(), document.getUploadedAt());
    }
}
