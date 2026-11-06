package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.api.AttendanceProofSubmitter;
import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TeacherAttendanceProofRecorder {

    private static final String DEFAULT_REASON = "Comprobante subido por el docente";

    private final AttendanceFinder attendanceFinder;
    private final AttendanceProofSubmitter proofSubmitter;
    private final FileStorageService fileStorageService;

    public TeacherAttendanceProofRecorder(
            AttendanceFinder attendanceFinder, AttendanceProofSubmitter proofSubmitter, FileStorageService fileStorageService) {
        this.attendanceFinder = attendanceFinder;
        this.proofSubmitter = proofSubmitter;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public AttendanceResponse recordProof(Long attendanceId, Long instructorId, String reason, MultipartFile proof) {
        Attendance attendance = attendanceFinder.findOrThrowOwnedBy(attendanceId, instructorId);
        String contentType = proof.getContentType();
        boolean allowed = MediaType.APPLICATION_PDF_VALUE.equals(contentType)
                || MediaType.IMAGE_JPEG_VALUE.equals(contentType);
        if (!allowed) {
            throw new UnsupportedFileTypeException(contentType);
        }
        String relativePath = fileStorageService.store(attendance.studentId(), "justificaciones-asistencia", proof);
        String effectiveReason = reason != null && !reason.isBlank() ? reason : DEFAULT_REASON;
        proofSubmitter.submitProof(attendanceId, effectiveReason, relativePath);
        return AttendanceResponse.from(attendanceFinder.findOrThrow(attendanceId));
    }
}
