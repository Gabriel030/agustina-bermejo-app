package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.usecase.CheckRegularityUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/regular-certificates")
public class RegularCertificateController {

    private final CheckRegularityUseCase checkRegularityUseCase;

    public RegularCertificateController(CheckRegularityUseCase checkRegularityUseCase) {
        this.checkRegularityUseCase = checkRegularityUseCase;
    }

    @GetMapping("/eligibility")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public RegularityResponse eligibility(@RequestParam RegularityKind kind, @RequestParam Long enrollmentId) {
        return checkRegularityUseCase.check(kind, enrollmentId);
    }
}
