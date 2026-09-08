package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.academicstructure.service.ProgramService;
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

import com.red_escuela.backend.entity.StatusType;

import java.util.List;

@RestController
@RequestMapping("/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse crearCarrera(@Valid @RequestBody CreateProgramRequest request) {
        return ProgramResponse.from(programService.crearCarrera(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse editarCarrera(@PathVariable Long id, @RequestBody UpdateProgramRequest request) {
        return ProgramResponse.from(programService.editarCarrera(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void desactivar(@PathVariable Long id) {
        programService.desactivar(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivar(@PathVariable Long id) {
        programService.reactivar(id);
    }

    @GetMapping
    public List<ProgramResponse> listar(@RequestParam(required = false) StatusType status) {
        return programService.listar(status).stream().map(ProgramResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProgramResponse verDetalle(@PathVariable Long id) {
        return ProgramResponse.from(programService.verDetalle(id));
    }
}
