package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ReinstateEnrollmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/students/{studentId}/reenrollment")
public class ReenrollmentController {

    @PostMapping("/program/{programId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public void reinstateForProgram(@PathVariable Long studentId, @PathVariable Long programId,
                                     @Valid @RequestBody ReinstateEnrollmentRequest request) {
    }

    @PostMapping("/course/{courseId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public void reinstateForCourse(@PathVariable Long studentId, @PathVariable Long courseId,
                                    @Valid @RequestBody ReinstateEnrollmentRequest request) {
    }
}
