package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RejectRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificateRequestsUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RejectRegularCertificateRequestUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/regular-certificate-requests")
public class RegularCertificateRequestController {

    private final ListRegularCertificateRequestsUseCase listUseCase;
    private final RejectRegularCertificateRequestUseCase rejectUseCase;

    public RegularCertificateRequestController(ListRegularCertificateRequestsUseCase listUseCase,
                                               RejectRegularCertificateRequestUseCase rejectUseCase) {
        this.listUseCase = listUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public RegularCertificateRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRegularCertificateRequest request) {
        return rejectUseCase.reject(id, request.reason());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public List<RegularCertificateRequestResponse> list(@RequestParam(required = false) Long studentId,
                                                        @RequestParam(required = false) RegularRequestOutcome outcome) {
        return listUseCase.list(studentId, outcome);
    }
}
