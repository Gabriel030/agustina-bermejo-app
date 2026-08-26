package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateTermRequest;
import com.red_escuela.backend.dto.TermResponse;
import com.red_escuela.backend.dto.UpdateTermRequest;
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
    public TermResponse crearPeriodo(@Valid @RequestBody CreateTermRequest request) {
        return TermResponse.from(termService.crearPeriodo(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public TermResponse editarPeriodo(@PathVariable Long id, @RequestBody UpdateTermRequest request) {
        return TermResponse.from(termService.editarPeriodo(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void desactivar(@PathVariable Long id) {
        termService.desactivar(id);
    }

    @GetMapping
    public List<TermResponse> listar() {
        return termService.listar().stream().map(TermResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TermResponse verDetalle(@PathVariable Long id) {
        return TermResponse.from(termService.verDetalle(id));
    }
}
