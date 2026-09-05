package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.GraduationRequestResponse;
import com.red_escuela.backend.dto.RejectGraduationRequestRequest;
import com.red_escuela.backend.entity.GraduationRequestStatus;
import com.red_escuela.backend.service.GraduationRequestService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF-AL-15/RF-DI-05: verificación de deuda (Administrativo) y aprobación (Director). */
@RestController
public class GraduationRequestController {

    private final GraduationRequestService graduationRequestService;

    public GraduationRequestController(GraduationRequestService graduationRequestService) {
        this.graduationRequestService = graduationRequestService;
    }

    @PostMapping("/graduation-requests/{id}/verify-debt")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public GraduationRequestResponse verifyDebt(@PathVariable Long id) {
        return GraduationRequestResponse.from(graduationRequestService.verifyDebt(id));
    }

    @PostMapping("/graduation-requests/{id}/approve")
    @PreAuthorize("hasRole('DIRECTOR')")
    public GraduationRequestResponse approve(@PathVariable Long id) {
        return GraduationRequestResponse.from(graduationRequestService.approve(id));
    }

    @PostMapping("/graduation-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public GraduationRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectGraduationRequestRequest request) {
        return GraduationRequestResponse.from(graduationRequestService.reject(id, request.motivo()));
    }

    @GetMapping("/program-enrollments/{id}/graduation-requests")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GraduationRequestResponse> list(@PathVariable Long id) {
        return graduationRequestService.listByProgramEnrollment(id).stream()
                .map(GraduationRequestResponse::from).toList();
    }

    @GetMapping("/graduation-requests")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GraduationRequestResponse> listByStatus(@RequestParam GraduationRequestStatus status) {
        return graduationRequestService.list(status).stream().map(GraduationRequestResponse::from).toList();
    }

    @GetMapping("/graduation-requests/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public GraduationRequestResponse verDetalle(@PathVariable Long id) {
        return GraduationRequestResponse.from(graduationRequestService.verDetalle(id));
    }
}
