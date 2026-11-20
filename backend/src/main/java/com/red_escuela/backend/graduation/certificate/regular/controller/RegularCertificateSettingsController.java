package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSettingsResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.UpdateRegularCertificateSettingsRequest;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetRegularCertificateSettingsUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.UpdateRegularCertificateSettingsUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/certificate-settings/regular")
public class RegularCertificateSettingsController {

    private final GetRegularCertificateSettingsUseCase getUseCase;
    private final UpdateRegularCertificateSettingsUseCase updateUseCase;

    public RegularCertificateSettingsController(GetRegularCertificateSettingsUseCase getUseCase,
                                                UpdateRegularCertificateSettingsUseCase updateUseCase) {
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public RegularCertificateSettingsResponse get() {
        return getUseCase.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public RegularCertificateSettingsResponse update(@Valid @RequestBody UpdateRegularCertificateSettingsRequest request) {
        return updateUseCase.update(request);
    }
}
