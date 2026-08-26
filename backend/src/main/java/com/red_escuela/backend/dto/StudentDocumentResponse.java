package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.DocumentType;
import com.red_escuela.backend.entity.StudentDocument;

import java.time.LocalDateTime;

public record StudentDocumentResponse(Long id, DocumentType type, LocalDateTime uploadedAt) {

    public static StudentDocumentResponse from(StudentDocument document) {
        return new StudentDocumentResponse(document.getId(), document.getType(), document.getUploadedAt());
    }
}
