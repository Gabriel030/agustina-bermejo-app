package com.red_escuela.backend.graduation.controller;

import com.red_escuela.backend.graduation.dto.GraduationRequestResponse;
import com.red_escuela.backend.graduation.dto.RejectGraduationRequestRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.usecase.request.ApproveGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.request.GetGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.request.ListGraduationRequestsByProgramEnrollmentUseCase;
import com.red_escuela.backend.graduation.usecase.request.ListGraduationRequestsByStatusUseCase;
import com.red_escuela.backend.graduation.usecase.request.RejectGraduationRequestCommand;
import com.red_escuela.backend.graduation.usecase.request.RejectGraduationRequestUseCase;
import com.red_escuela.backend.graduation.dto.GraduationDebtSummaryResponse;
import com.red_escuela.backend.graduation.usecase.request.GetGraduationRequestDebtSummaryUseCase;
import com.red_escuela.backend.graduation.usecase.request.VerifyGraduationRequestDebtUseCase;
import jakarta.validation.Valid;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    private final GetGraduationRequestDebtSummaryUseCase getGraduationRequestDebtSummaryUseCase;
    private final ApproveGraduationRequestUseCase approveGraduationRequestUseCase;
    private final RejectGraduationRequestUseCase rejectGraduationRequestUseCase;
    private final ListGraduationRequestsByProgramEnrollmentUseCase listGraduationRequestsByProgramEnrollmentUseCase;
    private final ListGraduationRequestsByStatusUseCase listGraduationRequestsByStatusUseCase;
    private final GetGraduationRequestUseCase getGraduationRequestUseCase;

    public GraduationRequestController(
            VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebtUseCase,
            GetGraduationRequestDebtSummaryUseCase getGraduationRequestDebtSummaryUseCase,
            ApproveGraduationRequestUseCase approveGraduationRequestUseCase,
            RejectGraduationRequestUseCase rejectGraduationRequestUseCase,
            ListGraduationRequestsByProgramEnrollmentUseCase listGraduationRequestsByProgramEnrollmentUseCase,
            ListGraduationRequestsByStatusUseCase listGraduationRequestsByStatusUseCase,
            GetGraduationRequestUseCase getGraduationRequestUseCase) {
        this.verifyGraduationRequestDebtUseCase = verifyGraduationRequestDebtUseCase;
        this.getGraduationRequestDebtSummaryUseCase = getGraduationRequestDebtSummaryUseCase;
        this.approveGraduationRequestUseCase = approveGraduationRequestUseCase;
        this.rejectGraduationRequestUseCase = rejectGraduationRequestUseCase;
        this.listGraduationRequestsByProgramEnrollmentUseCase = listGraduationRequestsByProgramEnrollmentUseCase;
        this.listGraduationRequestsByStatusUseCase = listGraduationRequestsByStatusUseCase;
        this.getGraduationRequestUseCase = getGraduationRequestUseCase;
    }

    @PostMapping("/graduation-requests/{id}/verify-debt")
    @RequiresPermission(code = "graduation.verify_debt", legacyRoles = "ROLE_ADMINISTRATIVO")
    public GraduationRequestResponse verifyDebt(@PathVariable Long id) {
        return GraduationRequestResponse.from(verifyGraduationRequestDebtUseCase.verify(id));
    }

    @GetMapping("/graduation-requests/{id}/debt-summary")
    @RequiresPermission(code = "graduation.read_debt_summary", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public GraduationDebtSummaryResponse debtSummary(@PathVariable Long id) {
        return getGraduationRequestDebtSummaryUseCase.get(id);
    }

    @PostMapping("/graduation-requests/{id}/approve")
    @RequiresPermission(code = "graduation.approve_request", legacyRoles = "ROLE_DIRECTOR")
    public GraduationRequestResponse approve(@PathVariable Long id) {
        return GraduationRequestResponse.from(approveGraduationRequestUseCase.approve(id));
    }

    @PostMapping("/graduation-requests/{id}/reject")
    @RequiresPermission(code = "graduation.reject_request", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public GraduationRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectGraduationRequestRequest request) {
        return GraduationRequestResponse.from(
                rejectGraduationRequestUseCase.reject(new RejectGraduationRequestCommand(id, request.motivo())));
    }

    @GetMapping("/program-enrollments/{id}/graduation-requests")
    @RequiresPermission(code = "graduation.read_requests", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<GraduationRequestResponse> listByProgramEnrollment(@PathVariable Long id) {
        return listGraduationRequestsByProgramEnrollmentUseCase.list(id).stream()
                .map(GraduationRequestResponse::from).toList();
    }

    @GetMapping("/graduation-requests")
    @RequiresPermission(code = "graduation.read_requests", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<GraduationRequestResponse> listByStatus(@RequestParam GraduationRequestStatus status) {
        return listGraduationRequestsByStatusUseCase.list(status).stream().map(GraduationRequestResponse::from).toList();
    }

    @GetMapping("/graduation-requests/{id}")
    @RequiresPermission(code = "graduation.read_requests", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public GraduationRequestResponse get(@PathVariable Long id) {
        return GraduationRequestResponse.from(getGraduationRequestUseCase.get(id));
    }
}
