package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AssignInstructorRequest;
import com.red_escuela.backend.dto.CourseSubjectSectionResponse;
import com.red_escuela.backend.dto.CreateSectionRequest;
import com.red_escuela.backend.dto.SectionInstructorResponse;
import com.red_escuela.backend.dto.UpdateSectionRequest;
import com.red_escuela.backend.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.service.CourseSectionService;
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
public class CourseSectionController {

    private final CourseSectionService sectionService;

    public CourseSectionController(CourseSectionService sectionService) {
        this.sectionService = sectionService;
    }

    @PostMapping("/course-subjects/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public CourseSubjectSectionResponse createSection(@PathVariable Long id, @Valid @RequestBody CreateSectionRequest request) {
        return CourseSubjectSectionResponse.from(sectionService.createSection(id, request));
    }

    @PutMapping("/course-sections/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public CourseSubjectSectionResponse updateSection(@PathVariable Long id, @RequestBody UpdateSectionRequest request) {
        return CourseSubjectSectionResponse.from(sectionService.updateSection(id, request));
    }

    @PostMapping("/course-sections/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        sectionService.deactivate(id);
    }

    @PostMapping("/course-sections/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        sectionService.reactivate(id);
    }

    @GetMapping("/course-subjects/{id}/sections")
    public List<CourseSubjectSectionResponse> listByCourseSubject(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return sectionService.listByCourseSubject(id, status).stream().map(CourseSubjectSectionResponse::from).toList();
    }

    @GetMapping("/course-sections/{id}")
    public CourseSubjectSectionResponse verDetalle(@PathVariable Long id) {
        return CourseSubjectSectionResponse.from(sectionService.verDetalle(id));
    }

    @PostMapping("/course-sections/{id}/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public SectionInstructorResponse asignarDocente(@PathVariable Long id, @Valid @RequestBody AssignInstructorRequest request) {
        CourseSubjectSectionInstructor assignment = sectionService.asignarDocente(id, request);
        return toResponse(assignment);
    }

    @GetMapping("/course-sections/{id}/instructors")
    public List<SectionInstructorResponse> listInstructors(@PathVariable Long id) {
        return sectionService.listInstructors(id).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/course-sections/{id}/instructors/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void quitarDocente(@PathVariable Long id, @PathVariable Long assignmentId) {
        sectionService.quitarDocente(id, assignmentId);
    }

    private SectionInstructorResponse toResponse(CourseSubjectSectionInstructor assignment) {
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
