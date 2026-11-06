package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificateRequestsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/regular-certificate-requests")
public class RegularCertificateRequestController {

    private final ListRegularCertificateRequestsUseCase listUseCase;

    public RegularCertificateRequestController(ListRegularCertificateRequestsUseCase listUseCase) {
        this.listUseCase = listUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public List<RegularCertificateRequestResponse> list(@RequestParam(required = false) Long studentId,
                                                        @RequestParam(required = false) RegularRequestOutcome outcome) {
        return listUseCase.list(studentId, outcome);
    }
}
