package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.attendance.api.AttendanceProofSubmitter;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AcademicRecordAttendanceProofRecorder {

    private static final String DEFAULT_REASON = "Comprobante subido por el alumno";

    private final AttendanceProofSubmitter attendanceProofSubmitter;
    private final FileStorageService fileStorageService;

    public AcademicRecordAttendanceProofRecorder(
            AttendanceProofSubmitter attendanceProofSubmitter, FileStorageService fileStorageService) {
        this.attendanceProofSubmitter = attendanceProofSubmitter;
        this.fileStorageService = fileStorageService;
    }

    public StudentAttendanceResponse recordProof(Long studentId, Long attendanceId, String reason, MultipartFile proof) {
        String contentType = proof.getContentType();
        boolean allowed = MediaType.APPLICATION_PDF_VALUE.equals(contentType)
                || MediaType.IMAGE_JPEG_VALUE.equals(contentType);
        if (!allowed) {
            throw new UnsupportedFileTypeException(contentType);
        }
        String relativePath = fileStorageService.store(studentId, "justificaciones-asistencia", proof);
        String effectiveReason = reason != null && !reason.isBlank() ? reason : DEFAULT_REASON;
        return StudentAttendanceResponse.from(
                attendanceProofSubmitter.submitProof(attendanceId, effectiveReason, relativePath));
    }
}
