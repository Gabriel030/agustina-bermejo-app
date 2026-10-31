package com.red_escuela.backend.attendance.controller;

import com.red_escuela.backend.attendance.domain.AttendanceJustificationStatus;
import com.red_escuela.backend.attendance.dto.AttendanceJustificationPage;
import com.red_escuela.backend.attendance.usecase.attendance.ListAttendanceJustificationsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceJustificationController {

    private final ListAttendanceJustificationsUseCase listAttendanceJustificationsUseCase;

    public AttendanceJustificationController(ListAttendanceJustificationsUseCase listAttendanceJustificationsUseCase) {
        this.listAttendanceJustificationsUseCase = listAttendanceJustificationsUseCase;
    }

    @GetMapping("/attendance/justifications")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceJustificationPage listJustifications(
            @RequestParam(defaultValue = "PENDING") AttendanceJustificationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return listAttendanceJustificationsUseCase.list(status, page, size);
    }
}
