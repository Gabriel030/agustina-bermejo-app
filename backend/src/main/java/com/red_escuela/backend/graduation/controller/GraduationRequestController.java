package com.red_escuela.backend.graduation.controller;

import com.red_escuela.backend.graduation.dto.GraduationRequestResponse;
import com.red_escuela.backend.graduation.dto.RejectGraduationRequestRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.usecase.graduationrequest.ApproveGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.GetGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.ListGraduationRequestsByProgramEnrollmentUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.ListGraduationRequestsByStatusUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RejectGraduationRequestCommand;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RejectGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.VerifyGraduationRequestDebtUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GraduationRequestController {

    private final VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebtUseCase;
    private final ApproveGraduationRequestUseCase approveGraduationRequestUseCase;
    private final RejectGraduationRequestUseCase rejectGraduationRequestUseCase;
    private final ListGraduationRequestsByProgramEnrollmentUseCase listGraduationRequestsByProgramEnrollmentUseCase;
    private final ListGraduationRequestsByStatusUseCase listGraduationRequestsByStatusUseCase;
    private final GetGraduationRequestUseCase getGraduationRequestUseCase;

    public GraduationRequestController(
            VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebtUseCase,
            ApproveGraduationRequestUseCase approveGraduationRequestUseCase,
            RejectGraduationRequestUseCase rejectGraduationRequestUseCase,
            ListGraduationRequestsByProgramEnrollmentUseCase listGraduationRequestsByProgramEnrollmentUseCase,
            ListGraduationRequestsByStatusUseCase listGraduationRequestsByStatusUseCase,
            GetGraduationRequestUseCase getGraduationRequestUseCase) {
        this.verifyGraduationRequestDebtUseCase = verifyGraduationRequestDebtUseCase;
        this.approveGraduationRequestUseCase = approveGraduationRequestUseCase;
        this.rejectGraduationRequestUseCase = rejectGraduationRequestUseCase;
        this.listGraduationRequestsByProgramEnrollmentUseCase = listGraduationRequestsByProgramEnrollmentUseCase;
        this.listGraduationRequestsByStatusUseCase = listGraduationRequestsByStatusUseCase;
        this.getGraduationRequestUseCase = getGraduationRequestUseCase;
    }

    @PostMapping("/graduation-requests/{id}/verify-debt")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public GraduationRequestResponse verifyDebt(@PathVariable Long id) {
        return GraduationRequestResponse.from(verifyGraduationRequestDebtUseCase.verify(id));
    }

    @PostMapping("/graduation-requests/{id}/approve")
    @PreAuthorize("hasRole('DIRECTOR')")
    public GraduationRequestResponse approve(@PathVariable Long id) {
        return GraduationRequestResponse.from(approveGraduationRequestUseCase.approve(id));
    }

    @PostMapping("/graduation-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public GraduationRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectGraduationRequestRequest request) {
        return GraduationRequestResponse.from(
                rejectGraduationRequestUseCase.reject(new RejectGraduationRequestCommand(id, request.motivo())));
    }

    @GetMapping("/program-enrollments/{id}/graduation-requests")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GraduationRequestResponse> listByProgramEnrollment(@PathVariable Long id) {
        return listGraduationRequestsByProgramEnrollmentUseCase.list(id).stream()
                .map(GraduationRequestResponse::from).toList();
    }

    @GetMapping("/graduation-requests")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GraduationRequestResponse> listByStatus(@RequestParam GraduationRequestStatus status) {
        return listGraduationRequestsByStatusUseCase.list(status).stream().map(GraduationRequestResponse::from).toList();
    }

    @GetMapping("/graduation-requests/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public GraduationRequestResponse get(@PathVariable Long id) {
        return GraduationRequestResponse.from(getGraduationRequestUseCase.get(id));
    }
}
