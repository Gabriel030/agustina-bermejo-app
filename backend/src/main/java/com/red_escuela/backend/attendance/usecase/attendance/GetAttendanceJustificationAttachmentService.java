package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceJustificationAttachment;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.storage.FileStorageService;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@UseCase
public class GetAttendanceJustificationAttachmentService implements GetAttendanceJustificationAttachmentUseCase {

    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final AttendanceRepository attendanceRepository;
    private final FileStorageService fileStorageService;

    public GetAttendanceJustificationAttachmentService(AttendanceRepository attendanceRepository, FileStorageService fileStorageService) {
        this.attendanceRepository = attendanceRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public Optional<AttendanceJustificationAttachment> get(Long attendanceId) {
        return attendanceRepository.findJustificationAttachmentKeyById(attendanceId)
                .filter(fileStorageService::exists)
                .map(storageKey -> {
                    String fileName = Path.of(storageKey).getFileName().toString();
                    return new AttendanceJustificationAttachment(fileName, contentTypeOf(fileName), fileStorageService.readBytes(storageKey));
                });
    }

    private String contentTypeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_CONTENT_TYPE;
        }
        return CONTENT_TYPES_BY_EXTENSION.getOrDefault(fileName.substring(dot + 1).toLowerCase(Locale.ROOT), DEFAULT_CONTENT_TYPE);
    }
}
