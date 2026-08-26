package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.dto.PrerequisiteResponse;
import com.red_escuela.backend.dto.ProgramSubjectResponse;
import com.red_escuela.backend.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.service.ProgramSubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProgramSubjectController {

    private final ProgramSubjectService programSubjectService;

    public ProgramSubjectController(ProgramSubjectService programSubjectService) {
        this.programSubjectService = programSubjectService;
    }

    @PostMapping("/programs/{id}/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramSubjectResponse agregarMateria(@PathVariable Long id, @Valid @RequestBody CreateProgramSubjectRequest request) {
        return ProgramSubjectResponse.from(programSubjectService.agregarMateria(id, request));
    }

    @PutMapping("/program-subjects/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramSubjectResponse editarMateria(@PathVariable Long id, @RequestBody UpdateProgramSubjectRequest request) {
        return ProgramSubjectResponse.from(programSubjectService.editarMateria(id, request));
    }

    @GetMapping("/programs/{id}/subjects")
    public List<ProgramSubjectResponse> listarPorCarrera(@PathVariable Long id) {
        return programSubjectService.listarPorCarrera(id).stream().map(ProgramSubjectResponse::from).toList();
    }

    @PostMapping("/program-subjects/{id}/prerequisites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PrerequisiteResponse agregarCorrelatividad(@PathVariable Long id, @Valid @RequestBody AddPrerequisiteRequest request) {
        return PrerequisiteResponse.from(programSubjectService.agregarCorrelatividad(id, request));
    }

    @GetMapping("/program-subjects/{id}/prerequisites")
    public List<PrerequisiteResponse> listarCorrelatividades(@PathVariable Long id) {
        return programSubjectService.listarCorrelatividades(id).stream().map(PrerequisiteResponse::from).toList();
    }

    @DeleteMapping("/program-subjects/prerequisites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void quitarCorrelatividad(@PathVariable Long id) {
        programSubjectService.quitarCorrelatividad(id);
    }
}
