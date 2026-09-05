package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.dto.ProgramProgressResponse;
import com.red_escuela.backend.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.service.ProgramEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProgramEnrollmentController {

    private final ProgramEnrollmentService programEnrollmentService;

    public ProgramEnrollmentController(ProgramEnrollmentService programEnrollmentService) {
        this.programEnrollmentService = programEnrollmentService;
    }

    @PostMapping("/students/{id}/program-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateProgramEnrollmentRequest request) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.enroll(id, request));
    }

    @PostMapping("/program-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void withdraw(@PathVariable Long id) {
        programEnrollmentService.withdraw(id);
    }

    @GetMapping("/students/{id}/program-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<ProgramEnrollmentResponse> listByStudent(@PathVariable Long id) {
        return programEnrollmentService.listByStudent(id).stream().map(ProgramEnrollmentResponse::from).toList();
    }

    @GetMapping("/program-enrollments/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ProgramEnrollmentResponse viewDetail(@PathVariable Long id) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.viewDetail(id));
    }

    @GetMapping("/program-enrollments/{id}/progress")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ProgramProgressResponse viewProgress(@PathVariable Long id) {
        return programEnrollmentService.calculateProgress(id);
    }

    @GetMapping("/program-enrollments/{id}/attendance-summary")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<SubjectAttendanceSummaryResponse> viewAttendanceSummary(@PathVariable Long id) {
        return programEnrollmentService.attendanceSummaryBySubject(id);
    }
}
