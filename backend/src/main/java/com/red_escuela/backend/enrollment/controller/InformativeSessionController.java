package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.SaveInformativeSessionRequest;
import com.red_escuela.backend.enrollment.usecase.informativesession.CancelInformativeSessionUseCase;
import com.red_escuela.backend.enrollment.usecase.informativesession.CreateInformativeSessionUseCase;
import com.red_escuela.backend.enrollment.usecase.informativesession.UpdateInformativeSessionCommand;
import com.red_escuela.backend.enrollment.usecase.informativesession.UpdateInformativeSessionUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/informative-sessions")
public class InformativeSessionController {

    private static final String LEGACY_ROLES = "ROLE_PRECEPTOR,ROLE_DIRECTOR";

    private final CreateInformativeSessionUseCase createInformativeSessionUseCase;
    private final UpdateInformativeSessionUseCase updateInformativeSessionUseCase;
    private final CancelInformativeSessionUseCase cancelInformativeSessionUseCase;

    public InformativeSessionController(CreateInformativeSessionUseCase createInformativeSessionUseCase,
                                        UpdateInformativeSessionUseCase updateInformativeSessionUseCase,
                                        CancelInformativeSessionUseCase cancelInformativeSessionUseCase) {
        this.createInformativeSessionUseCase = createInformativeSessionUseCase;
        this.updateInformativeSessionUseCase = updateInformativeSessionUseCase;
        this.cancelInformativeSessionUseCase = cancelInformativeSessionUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "informative_session.manage", legacyRoles = LEGACY_ROLES)
    public InformativeSessionResponse create(@Valid @RequestBody SaveInformativeSessionRequest request) {
        return createInformativeSessionUseCase.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(code = "informative_session.manage", legacyRoles = LEGACY_ROLES)
    public InformativeSessionResponse update(@PathVariable Long id, @Valid @RequestBody SaveInformativeSessionRequest request) {
        return updateInformativeSessionUseCase.update(new UpdateInformativeSessionCommand(id, request));
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission(code = "informative_session.manage", legacyRoles = LEGACY_ROLES)
    public InformativeSessionResponse cancel(@PathVariable Long id) {
        return cancelInformativeSessionUseCase.cancel(id);
    }
}
