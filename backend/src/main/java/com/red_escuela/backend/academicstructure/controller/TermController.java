package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.academicstructure.dto.TermResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.academicstructure.usecase.term.ActivateTermUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.ClearTermEnrollmentOverrideUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.CloseTermEnrollmentUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.CreateTermUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.DeactivateTermUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.GetTermUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.ListTermsUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.OpenTermEnrollmentUseCase;
import com.red_escuela.backend.academicstructure.usecase.term.UpdateTermCommand;
import com.red_escuela.backend.academicstructure.usecase.term.UpdateTermUseCase;
import com.red_escuela.backend.entity.StatusType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/terms")
public class TermController {

    private final CreateTermUseCase createTermUseCase;
    private final UpdateTermUseCase updateTermUseCase;
    private final DeactivateTermUseCase deactivateTermUseCase;
    private final ActivateTermUseCase activateTermUseCase;
    private final ListTermsUseCase listTermsUseCase;
    private final GetTermUseCase getTermUseCase;
    private final OpenTermEnrollmentUseCase openTermEnrollmentUseCase;
    private final CloseTermEnrollmentUseCase closeTermEnrollmentUseCase;
    private final ClearTermEnrollmentOverrideUseCase clearTermEnrollmentOverrideUseCase;

    public TermController(CreateTermUseCase createTermUseCase, UpdateTermUseCase updateTermUseCase,
                           DeactivateTermUseCase deactivateTermUseCase, ActivateTermUseCase activateTermUseCase,
                           ListTermsUseCase listTermsUseCase, GetTermUseCase getTermUseCase,
                           OpenTermEnrollmentUseCase openTermEnrollmentUseCase,
                           CloseTermEnrollmentUseCase closeTermEnrollmentUseCase,
                           ClearTermEnrollmentOverrideUseCase clearTermEnrollmentOverrideUseCase) {
        this.createTermUseCase = createTermUseCase;
        this.updateTermUseCase = updateTermUseCase;
        this.deactivateTermUseCase = deactivateTermUseCase;
        this.activateTermUseCase = activateTermUseCase;
        this.listTermsUseCase = listTermsUseCase;
        this.getTermUseCase = getTermUseCase;
        this.openTermEnrollmentUseCase = openTermEnrollmentUseCase;
        this.closeTermEnrollmentUseCase = closeTermEnrollmentUseCase;
        this.clearTermEnrollmentOverrideUseCase = clearTermEnrollmentOverrideUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public TermResponse crearPeriodo(@Valid @RequestBody CreateTermRequest request) {
        return TermResponse.from(createTermUseCase.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public TermResponse editarPeriodo(@PathVariable Long id, @RequestBody UpdateTermRequest request) {
        return TermResponse.from(updateTermUseCase.update(new UpdateTermCommand(id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void desactivar(@PathVariable Long id) {
        deactivateTermUseCase.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void reactivar(@PathVariable Long id) {
        activateTermUseCase.activate(id);
    }

    @GetMapping
    public List<TermResponse> listar(@RequestParam(required = false) StatusType status) {
        return listTermsUseCase.list(status).stream().map(TermResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TermResponse verDetalle(@PathVariable Long id) {
        return TermResponse.from(getTermUseCase.get(id));
    }

    // Decisión 5 (Spec Procesos administrativos): override manual de ventana de inscripción.
    // Preceptor comparte esto con Administrativo (decisión académica, no financiera).
    @PostMapping("/{id}/enrollment/open")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void abrirInscripcion(@PathVariable Long id) {
        openTermEnrollmentUseCase.open(id);
    }

    @PostMapping("/{id}/enrollment/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void cerrarInscripcion(@PathVariable Long id) {
        closeTermEnrollmentUseCase.close(id);
    }

    @PostMapping("/{id}/enrollment/clear-override")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void quitarOverride(@PathVariable Long id) {
        clearTermEnrollmentOverrideUseCase.clear(id);
    }
}
