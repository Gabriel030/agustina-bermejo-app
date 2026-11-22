package com.red_escuela.backend.graduation.documentsettings.controller;

import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.dto.UpdateDocumentIssuanceSettingsRequest;
import com.red_escuela.backend.graduation.documentsettings.usecase.GetDocumentIssuanceSettingsUseCase;
import com.red_escuela.backend.graduation.documentsettings.usecase.UpdateDocumentIssuanceSettingsUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/certificate-settings/documents")
public class DocumentIssuanceSettingsController {

    private final GetDocumentIssuanceSettingsUseCase getUseCase;
    private final UpdateDocumentIssuanceSettingsUseCase updateUseCase;

    public DocumentIssuanceSettingsController(GetDocumentIssuanceSettingsUseCase getUseCase,
                                              UpdateDocumentIssuanceSettingsUseCase updateUseCase) {
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public DocumentIssuanceSettingsResponse get() {
        return getUseCase.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public DocumentIssuanceSettingsResponse update(@Valid @RequestBody UpdateDocumentIssuanceSettingsRequest request) {
        return updateUseCase.update(request);
    }
}
