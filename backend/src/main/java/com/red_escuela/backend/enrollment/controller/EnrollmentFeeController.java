package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.EnrollmentFeeExemptionResponse;
import com.red_escuela.backend.enrollment.dto.EnrollmentFeeObligationResponse;
import com.red_escuela.backend.enrollment.dto.GrantEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.enrollment.dto.RevokeEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.enrollment.usecase.enrollmentfee.GetEnrollmentFeeStatusUseCase;
import com.red_escuela.backend.enrollment.usecase.enrollmentfee.GrantEnrollmentFeeExemptionUseCase;
import com.red_escuela.backend.enrollment.usecase.enrollmentfee.ListEnrollmentFeeExemptionsUseCase;
import com.red_escuela.backend.enrollment.usecase.enrollmentfee.RevokeEnrollmentFeeExemptionUseCase;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentStudentService;
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
public class EnrollmentFeeController {
    private final GrantEnrollmentFeeExemptionUseCase grantUseCase;
    private final RevokeEnrollmentFeeExemptionUseCase revokeUseCase;
    private final ListEnrollmentFeeExemptionsUseCase listUseCase;
    private final GetEnrollmentFeeStatusUseCase statusUseCase;
    private final CurrentStudentService currentStudentService;
    private final ProgramEnrollmentService programEnrollmentService;

    public EnrollmentFeeController(GrantEnrollmentFeeExemptionUseCase grantUseCase, RevokeEnrollmentFeeExemptionUseCase revokeUseCase,
                                   ListEnrollmentFeeExemptionsUseCase listUseCase, GetEnrollmentFeeStatusUseCase statusUseCase,
                                   CurrentStudentService currentStudentService, ProgramEnrollmentService programEnrollmentService) {
        this.grantUseCase = grantUseCase;
        this.revokeUseCase = revokeUseCase;
        this.listUseCase = listUseCase;
        this.statusUseCase = statusUseCase;
        this.currentStudentService = currentStudentService;
        this.programEnrollmentService = programEnrollmentService;
    }

    @PostMapping("/program-enrollments/{id}/enrollment-fee-exemptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public EnrollmentFeeExemptionResponse grant(@PathVariable Long id, @Valid @RequestBody GrantEnrollmentFeeExemptionRequest request) {
        return EnrollmentFeeExemptionResponse.from(grantUseCase.grant(id, request));
    }

    @PostMapping("/enrollment-fee-exemptions/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public void revoke(@PathVariable Long id, @Valid @RequestBody RevokeEnrollmentFeeExemptionRequest request) {
        revokeUseCase.revoke(id, request.notes());
    }

    @GetMapping("/program-enrollments/{id}/enrollment-fee-exemptions")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO', 'PRECEPTOR')")
    public List<EnrollmentFeeExemptionResponse> list(@PathVariable Long id) {
        return listUseCase.list(id);
    }

    @GetMapping("/program-enrollments/{id}/enrollment-fee-status")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO', 'PRECEPTOR')")
    public List<EnrollmentFeeObligationResponse> status(@PathVariable Long id) {
        return statusUseCase.get(id);
    }

    @GetMapping("/me/program-enrollments/{id}/enrollment-fee-status")
    public List<EnrollmentFeeObligationResponse> myStatus(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment enrollment = programEnrollmentService.get(id);
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentNotFoundException(id);
        }
        return statusUseCase.get(id);
    }
}
