package com.red_escuela.backend.enrollment.dto;

import org.springframework.web.multipart.MultipartFile;

public record ApplicationDocumentFiles(MultipartFile dniFront, MultipartFile dniBack, MultipartFile academicTranscript) {
}
