package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.dto.ScholarshipResponse;
import com.red_escuela.backend.billing.usecase.scholarship.GrantScholarshipForCourseUseCase;
import com.red_escuela.backend.billing.usecase.scholarship.GrantScholarshipForProgramUseCase;
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

    private final GrantScholarshipForCourseUseCase grantForCourseUseCase;
    private final GrantScholarshipForProgramUseCase grantForProgramUseCase;

    public ScholarshipController(GrantScholarshipForCourseUseCase grantForCourseUseCase, GrantScholarshipForProgramUseCase grantForProgramUseCase) {
        this.grantForCourseUseCase = grantForCourseUseCase;
        this.grantForProgramUseCase = grantForProgramUseCase;
    }

    @PostMapping("/course-enrollments/{id}/scholarships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ScholarshipResponse grantForCourse(@PathVariable Long id, @Valid @RequestBody CreateScholarshipRequest request) {
        return ScholarshipResponse.from(grantForCourseUseCase.grant(id, request));
    }

    @PostMapping("/program-enrollments/{id}/scholarships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ScholarshipResponse grantForProgram(@PathVariable Long id, @Valid @RequestBody CreateScholarshipRequest request) {
        return ScholarshipResponse.from(grantForProgramUseCase.grant(id, request));
    }
}
