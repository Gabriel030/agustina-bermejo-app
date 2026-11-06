package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.AcademicSettingsRequest;
import com.red_escuela.backend.identity.dto.AcademicSettingsResponse;
import com.red_escuela.backend.identity.usecase.institution.GetAcademicSettingsUseCase;
import com.red_escuela.backend.identity.usecase.institution.UpdateAcademicSettingsUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institution/academic-settings")
public class InstitutionAcademicSettingsController {

    private final GetAcademicSettingsUseCase getAcademicSettings;
    private final UpdateAcademicSettingsUseCase updateAcademicSettings;

    public InstitutionAcademicSettingsController(
            GetAcademicSettingsUseCase getAcademicSettings, UpdateAcademicSettingsUseCase updateAcademicSettings) {
        this.getAcademicSettings = getAcademicSettings;
        this.updateAcademicSettings = updateAcademicSettings;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRATIVO','PRECEPTOR','DOCENTE')")
    public AcademicSettingsResponse get() {
        return getAcademicSettings.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public AcademicSettingsResponse update(@Valid @RequestBody AcademicSettingsRequest request) {
        return updateAcademicSettings.update(request);
    }
}
