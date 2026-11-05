package com.red_escuela.backend.attendance.controller;

import com.red_escuela.backend.attendance.domain.AttendanceJustificationStatus;
import com.red_escuela.backend.attendance.dto.AttendanceJustificationPage;
import com.red_escuela.backend.attendance.usecase.attendance.ListAttendanceJustificationsUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetAttendanceJustificationAttachmentUseCase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceJustificationController {

    private final ListAttendanceJustificationsUseCase listAttendanceJustificationsUseCase;

    private final GetAttendanceJustificationAttachmentUseCase getAttendanceJustificationAttachmentUseCase;

    public AttendanceJustificationController(ListAttendanceJustificationsUseCase listAttendanceJustificationsUseCase,
                                             GetAttendanceJustificationAttachmentUseCase getAttendanceJustificationAttachmentUseCase) {
        this.listAttendanceJustificationsUseCase = listAttendanceJustificationsUseCase;
        this.getAttendanceJustificationAttachmentUseCase = getAttendanceJustificationAttachmentUseCase;
    }

    @GetMapping("/attendance/justifications")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceJustificationPage listJustifications(
            @RequestParam(defaultValue = "PENDING") AttendanceJustificationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return listAttendanceJustificationsUseCase.list(status, page, size);
    }

    @GetMapping("/attendance/{id}/justification-attachment")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ResponseEntity<byte[]> getJustificationAttachment(@PathVariable Long id) {
        return getAttendanceJustificationAttachmentUseCase.get(id)
                .map(attachment -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(attachment.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(attachment.fileName()).build().toString())
                        .body(attachment.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
