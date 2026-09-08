package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import org.springframework.web.multipart.MultipartFile;

public record AddProgramEnrollmentApplicationDocumentCommand(Long applicationId, DocumentType type, MultipartFile file) {
}
