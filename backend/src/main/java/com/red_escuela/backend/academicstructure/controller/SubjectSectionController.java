package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.SectionInstructorResponse;
import com.red_escuela.backend.academicstructure.dto.SubjectSectionResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SubjectSectionController {

    private final SubjectSectionService sectionService;

    public SubjectSectionController(SubjectSectionService sectionService) {
        this.sectionService = sectionService;
    }

    @PostMapping("/subjects/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SubjectSectionResponse crearComision(@PathVariable Long id, @Valid @RequestBody CreateSectionRequest request) {
        return SubjectSectionResponse.from(sectionService.crearComision(id, request));
    }

    @PutMapping("/program-sections/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SubjectSectionResponse editarComision(@PathVariable Long id, @RequestBody UpdateSectionRequest request) {
        return SubjectSectionResponse.from(sectionService.editarComision(id, request));
    }

    @PostMapping("/program-sections/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void desactivar(@PathVariable Long id) {
        sectionService.desactivar(id);
    }

    @PostMapping("/program-sections/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void reactivar(@PathVariable Long id) {
        sectionService.reactivar(id);
    }

    @GetMapping("/subjects/{id}/sections")
    public List<SubjectSectionResponse> listarPorMateria(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return sectionService.listarPorMateria(id, status).stream().map(SubjectSectionResponse::from).toList();
    }

    @GetMapping("/program-sections/{id}")
    public SubjectSectionResponse verDetalle(@PathVariable Long id) {
        return SubjectSectionResponse.from(sectionService.verDetalle(id));
    }

    @PostMapping("/program-sections/{id}/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SectionInstructorResponse asignarDocente(@PathVariable Long id, @Valid @RequestBody AssignInstructorRequest request) {
        SubjectSectionInstructor assignment = sectionService.asignarDocente(id, request);
        return toResponse(assignment);
    }

    @GetMapping("/program-sections/{id}/instructors")
    public List<SectionInstructorResponse> listarDocentes(@PathVariable Long id) {
        return sectionService.listarDocentes(id).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/program-sections/{id}/instructors/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void quitarDocente(@PathVariable Long id, @PathVariable Long assignmentId) {
        sectionService.quitarDocente(id, assignmentId);
    }

    private SectionInstructorResponse toResponse(SubjectSectionInstructor assignment) {
        return new SectionInstructorResponse(
                assignment.getId(),
                assignment.getInstructor().getId(),
                assignment.getInstructor().getPerson().getName() + " " + assignment.getInstructor().getPerson().getLastname(),
                assignment.getRole(),
                assignment.getStartAt(),
                assignment.getEndAt(),
                assignment.getHoursPerWeek());
    }
}
