package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.enrollment.dto.DecideDirectorApprovalRequestRequest;
import com.red_escuela.backend.enrollment.dto.DirectorApprovalRequestResponse;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.usecase.directorapproval.ApproveDirectorApprovalRequestUseCase;
import com.red_escuela.backend.enrollment.usecase.directorapproval.DecideDirectorApprovalRequestCommand;
import com.red_escuela.backend.enrollment.usecase.directorapproval.GetDirectorApprovalRequestUseCase;
import com.red_escuela.backend.enrollment.usecase.directorapproval.ListDirectorApprovalRequestsUseCase;
import com.red_escuela.backend.enrollment.usecase.directorapproval.RejectDirectorApprovalRequestUseCase;
import com.red_escuela.backend.enrollment.usecase.directorapproval.RequestDirectorApprovalUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/director-approval-requests")
public class DirectorApprovalRequestController {

    private final RequestDirectorApprovalUseCase requestDirectorApprovalUseCase;
    private final ListDirectorApprovalRequestsUseCase listDirectorApprovalRequestsUseCase;
    private final GetDirectorApprovalRequestUseCase getDirectorApprovalRequestUseCase;
    private final ApproveDirectorApprovalRequestUseCase approveDirectorApprovalRequestUseCase;
    private final RejectDirectorApprovalRequestUseCase rejectDirectorApprovalRequestUseCase;

    public DirectorApprovalRequestController(RequestDirectorApprovalUseCase requestDirectorApprovalUseCase,
                                              ListDirectorApprovalRequestsUseCase listDirectorApprovalRequestsUseCase,
                                              GetDirectorApprovalRequestUseCase getDirectorApprovalRequestUseCase,
                                              ApproveDirectorApprovalRequestUseCase approveDirectorApprovalRequestUseCase,
                                              RejectDirectorApprovalRequestUseCase rejectDirectorApprovalRequestUseCase) {
        this.requestDirectorApprovalUseCase = requestDirectorApprovalUseCase;
        this.listDirectorApprovalRequestsUseCase = listDirectorApprovalRequestsUseCase;
        this.getDirectorApprovalRequestUseCase = getDirectorApprovalRequestUseCase;
        this.approveDirectorApprovalRequestUseCase = approveDirectorApprovalRequestUseCase;
        this.rejectDirectorApprovalRequestUseCase = rejectDirectorApprovalRequestUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public DirectorApprovalRequestResponse solicitar(@Valid @RequestBody CreateDirectorApprovalRequestRequest request) {
        return DirectorApprovalRequestResponse.from(requestDirectorApprovalUseCase.request(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<DirectorApprovalRequestResponse> listar(@RequestParam(required = false) DirectorApprovalRequestStatus status) {
        return listDirectorApprovalRequestsUseCase.list(status).stream()
                .map(DirectorApprovalRequestResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DIRECTOR')")
    public DirectorApprovalRequestResponse verDetalle(@PathVariable Long id) {
        return DirectorApprovalRequestResponse.from(getDirectorApprovalRequestUseCase.get(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('DIRECTOR')")
    public DirectorApprovalRequestResponse aprobar(@PathVariable Long id, @RequestBody(required = false) DecideDirectorApprovalRequestRequest request) {
        String notas = request == null ? null : request.notas();
        return DirectorApprovalRequestResponse.from(
                approveDirectorApprovalRequestUseCase.approve(new DecideDirectorApprovalRequestCommand(id, notas)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('DIRECTOR')")
    public DirectorApprovalRequestResponse rechazar(@PathVariable Long id, @RequestBody(required = false) DecideDirectorApprovalRequestRequest request) {
        String notas = request == null ? null : request.notas();
        return DirectorApprovalRequestResponse.from(
                rejectDirectorApprovalRequestUseCase.reject(new DecideDirectorApprovalRequestCommand(id, notas)));
    }
}
