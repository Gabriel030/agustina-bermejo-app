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
    public SubjectSectionResponse create(@PathVariable Long id, @Valid @RequestBody CreateSectionRequest request) {
        return SubjectSectionResponse.from(sectionService.create(id, request));
    }

    @PutMapping("/program-sections/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SubjectSectionResponse update(@PathVariable Long id, @RequestBody UpdateSectionRequest request) {
        return SubjectSectionResponse.from(sectionService.update(id, request));
    }

    @PostMapping("/program-sections/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        sectionService.deactivate(id);
    }

    @PostMapping("/program-sections/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        sectionService.reactivate(id);
    }

    @GetMapping("/subjects/{id}/sections")
    public List<SubjectSectionResponse> listBySubject(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return sectionService.listBySubject(id, status).stream().map(SubjectSectionResponse::from).toList();
    }

    @GetMapping("/program-sections/{id}")
    public SubjectSectionResponse get(@PathVariable Long id) {
        return SubjectSectionResponse.from(sectionService.get(id));
    }

    @PostMapping("/program-sections/{id}/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SectionInstructorResponse assignInstructor(@PathVariable Long id, @Valid @RequestBody AssignInstructorRequest request) {
        SubjectSectionInstructor assignment = sectionService.assignInstructor(id, request);
        return toResponse(assignment);
    }

    @GetMapping("/program-sections/{id}/instructors")
    public List<SectionInstructorResponse> listInstructors(@PathVariable Long id) {
        return sectionService.listInstructors(id).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/program-sections/{id}/instructors/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void removeInstructor(@PathVariable Long id, @PathVariable Long assignmentId) {
        sectionService.removeInstructor(id, assignmentId);
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
