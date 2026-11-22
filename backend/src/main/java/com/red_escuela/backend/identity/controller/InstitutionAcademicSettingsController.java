package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.AcademicSettingsRequest;
import com.red_escuela.backend.identity.dto.AcademicSettingsResponse;
import com.red_escuela.backend.identity.usecase.institution.GetAcademicSettingsUseCase;
import com.red_escuela.backend.identity.usecase.institution.UpdateAcademicSettingsUseCase;
import jakarta.validation.Valid;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    @RequiresPermission(code = "identity.read_academic_settings", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO,ROLE_PRECEPTOR,ROLE_DOCENTE")
    public AcademicSettingsResponse get() {
        return getAcademicSettings.get();
    }

    @PutMapping
    @RequiresPermission(code = "identity.update_academic_settings", legacyRoles = "ROLE_DIRECTOR")
    public AcademicSettingsResponse update(@Valid @RequestBody AcademicSettingsRequest request) {
        return updateAcademicSettings.update(request);
    }
}
