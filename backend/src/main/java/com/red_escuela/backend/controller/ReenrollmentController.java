package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.ReinstateEnrollmentRequest;
import com.red_escuela.backend.entity.ReinstatementDecision;
import com.red_escuela.backend.service.CurrentPersonService;
import com.red_escuela.backend.service.StudentDebtResolutionService;
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

    private final StudentDebtResolutionService studentDebtResolutionService;
    private final CurrentPersonService currentPersonService;

    public ReenrollmentController(StudentDebtResolutionService studentDebtResolutionService, CurrentPersonService currentPersonService) {
        this.studentDebtResolutionService = studentDebtResolutionService;
        this.currentPersonService = currentPersonService;
    }

    @PostMapping("/program/{programId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('DIRECTOR') or (#request.decision().name() == 'SETTLE' and hasRole('ADMINISTRATIVO'))")
    public void reinstateForProgram(@PathVariable Long studentId, @PathVariable Long programId,
                                     @Valid @RequestBody ReinstateEnrollmentRequest request) {
        if (request.decision() == ReinstatementDecision.WAIVE) {
            studentDebtResolutionService.waiveDebtForProgram(
                    studentId, programId, "Waived on reinstatement", currentPersonService.resolveCurrentPerson());
        }
    }

    @PostMapping("/course/{courseId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('DIRECTOR') or (#request.decision().name() == 'SETTLE' and hasRole('ADMINISTRATIVO'))")
    public void reinstateForCourse(@PathVariable Long studentId, @PathVariable Long courseId,
                                    @Valid @RequestBody ReinstateEnrollmentRequest request) {
        if (request.decision() == ReinstatementDecision.WAIVE) {
            studentDebtResolutionService.waiveDebtForCourse(
                    studentId, courseId, "Waived on reinstatement", currentPersonService.resolveCurrentPerson());
        }
    }
}
