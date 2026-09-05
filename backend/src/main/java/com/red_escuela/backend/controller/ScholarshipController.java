package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateScholarshipRequest;
import com.red_escuela.backend.dto.ScholarshipResponse;
import com.red_escuela.backend.service.ScholarshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScholarshipController {

    private final ScholarshipService scholarshipService;

    public ScholarshipController(ScholarshipService scholarshipService) {
        this.scholarshipService = scholarshipService;
    }

    @PostMapping("/course-enrollments/{id}/scholarships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ScholarshipResponse grantForCourse(@PathVariable Long id, @Valid @RequestBody CreateScholarshipRequest request) {
        return ScholarshipResponse.from(scholarshipService.grantForCourse(id, request));
    }

    @PostMapping("/program-enrollments/{id}/scholarships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ScholarshipResponse grantForProgram(@PathVariable Long id, @Valid @RequestBody CreateScholarshipRequest request) {
        return ScholarshipResponse.from(scholarshipService.grantForProgram(id, request));
    }
}
