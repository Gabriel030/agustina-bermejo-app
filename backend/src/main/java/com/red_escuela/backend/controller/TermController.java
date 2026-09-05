package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateTermRequest;
import com.red_escuela.backend.dto.TermResponse;
import com.red_escuela.backend.dto.UpdateTermRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.service.TermService;
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

    private final TermService termService;

    public TermController(TermService termService) {
        this.termService = termService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public TermResponse createTerm(@Valid @RequestBody CreateTermRequest request) {
        return TermResponse.from(termService.createTerm(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public TermResponse updateTerm(@PathVariable Long id, @RequestBody UpdateTermRequest request) {
        return TermResponse.from(termService.updateTerm(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        termService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        termService.reactivate(id);
    }

    @GetMapping
    public List<TermResponse> list(@RequestParam(required = false) StatusType status) {
        return termService.list(status).stream().map(TermResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TermResponse verDetalle(@PathVariable Long id) {
        return TermResponse.from(termService.verDetalle(id));
    }

    // Decisión 5 (Spec Procesos administrativos): override manual de ventana de inscripción.
    // Preceptor comparte esto con Administrativo (decisión académica, no financiera).
    @PostMapping("/{id}/enrollment/open")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void abrirInscripcion(@PathVariable Long id) {
        termService.abrirInscripcion(id);
    }

    @PostMapping("/{id}/enrollment/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void cerrarInscripcion(@PathVariable Long id) {
        termService.cerrarInscripcion(id);
    }

    @PostMapping("/{id}/enrollment/clear-override")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void quitarOverride(@PathVariable Long id) {
        termService.quitarOverride(id);
    }
}
