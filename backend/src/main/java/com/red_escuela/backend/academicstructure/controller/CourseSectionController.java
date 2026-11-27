package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.CourseSubjectSectionResponse;
import com.red_escuela.backend.academicstructure.dto.SectionInstructorResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.service.CourseSectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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

    @PutMapping("/course-sections/{id}")
    @RequiresPermission(code = "academicstructure.manage_sections", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public CourseSubjectSectionResponse update(@PathVariable Long id, @RequestBody UpdateSectionRequest request) {
        return CourseSubjectSectionResponse.from(sectionService.update(id, request));
    }

    @PostMapping("/course-sections/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_section_status", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public void deactivate(@PathVariable Long id) {
        sectionService.deactivate(id);
    }

    @PostMapping("/course-sections/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_section_status", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public void reactivate(@PathVariable Long id) {
        sectionService.reactivate(id);
    }

    @GetMapping("/course-subjects/{id}/sections")
    public List<CourseSubjectSectionResponse> listByCourseSubject(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return sectionService.listByCourseSubject(id, status).stream().map(CourseSubjectSectionResponse::from).toList();
    }

    @GetMapping("/course-sections/{id}")
    public CourseSubjectSectionResponse get(@PathVariable Long id) {
        return CourseSubjectSectionResponse.from(sectionService.get(id));
    }

    @PostMapping("/course-sections/{id}/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.assign_section_instructor", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public SectionInstructorResponse assignInstructor(@PathVariable Long id, @Valid @RequestBody AssignInstructorRequest request) {
        CourseSubjectSectionInstructor assignment = sectionService.assignInstructor(id, request);
        return toResponse(assignment);
    }

    @GetMapping("/course-sections/{id}/instructors")
    public List<SectionInstructorResponse> listInstructors(@PathVariable Long id) {
        return sectionService.listInstructors(id).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/course-sections/{id}/instructors/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.remove_section_instructor", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public void removeInstructor(@PathVariable Long id, @PathVariable Long assignmentId) {
        sectionService.removeInstructor(id, assignmentId);
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
