package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.dto.DecideDirectorApprovalRequestRequest;
import com.red_escuela.backend.dto.DirectorApprovalRequestResponse;
import com.red_escuela.backend.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.service.DirectorApprovalRequestService;
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

/**
 * RF-DI-03: solicitud/aprobación/rechazo de excepciones del Director. Decisión 12: sin efecto
 * real sobre otros services — el APPROVED queda registrado como decisión, no destraba ninguna
 * validación (deuda técnica documentada explícitamente en la spec).
 */
@RestController
@RequestMapping("/director-approval-requests")
public class DirectorApprovalRequestController {

    private final DirectorApprovalRequestService directorApprovalRequestService;

    public DirectorApprovalRequestController(DirectorApprovalRequestService directorApprovalRequestService) {
        this.directorApprovalRequestService = directorApprovalRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public DirectorApprovalRequestResponse solicitar(@Valid @RequestBody CreateDirectorApprovalRequestRequest request) {
        return DirectorApprovalRequestResponse.from(directorApprovalRequestService.solicitar(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<DirectorApprovalRequestResponse> listar(@RequestParam(required = false) DirectorApprovalRequestStatus status) {
        return directorApprovalRequestService.listar(status).stream()
                .map(DirectorApprovalRequestResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DIRECTOR')")
    public DirectorApprovalRequestResponse verDetalle(@PathVariable Long id) {
        return DirectorApprovalRequestResponse.from(directorApprovalRequestService.verDetalle(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('DIRECTOR')")
    public DirectorApprovalRequestResponse aprobar(@PathVariable Long id, @RequestBody(required = false) DecideDirectorApprovalRequestRequest request) {
        String notas = request != null ? request.notas() : null;
        return DirectorApprovalRequestResponse.from(directorApprovalRequestService.aprobar(id, notas));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('DIRECTOR')")
    public DirectorApprovalRequestResponse rechazar(@PathVariable Long id, @RequestBody(required = false) DecideDirectorApprovalRequestRequest request) {
        String notas = request != null ? request.notas() : null;
        return DirectorApprovalRequestResponse.from(directorApprovalRequestService.rechazar(id, notas));
    }
}
