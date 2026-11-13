package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.billing.dto.CoursePricingResponse;
import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.SetCatalogVisibilityRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.service.CourseService;
import com.red_escuela.backend.academicstructure.usecase.SetCourseCatalogVisibilityUseCase;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
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
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;
    private final SetCourseCatalogVisibilityUseCase setCatalogVisibility;
    private final CourseEnrollmentService courseEnrollmentService;

    public CourseController(CourseService courseService,
            SetCourseCatalogVisibilityUseCase setCatalogVisibility,
            CourseEnrollmentService courseEnrollmentService) {
        this.courseService = courseService;
        this.setCatalogVisibility = setCatalogVisibility;
        this.courseEnrollmentService = courseEnrollmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseResponse create(@Valid @RequestBody CreateCourseRequest request) {
        return CourseResponse.from(courseService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest request) {
        return CourseResponse.from(courseService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        courseService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        courseService.reactivate(id);
    }

    @GetMapping
    public List<CourseResponse> list(@RequestParam(required = false) StatusType status,
            @RequestParam(required = false) Boolean visibleInCatalog) {
        return courseService.list(status, visibleInCatalog).stream().map(CourseResponse::from).toList();
    }

    @PutMapping("/{id}/catalog-visibility")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseResponse setCatalogVisibility(@PathVariable Long id,
            @Valid @RequestBody SetCatalogVisibilityRequest request) {
        return setCatalogVisibility.execute(id, request.visible());
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable Long id) {
        return CourseResponse.from(courseService.get(id), courseEnrollmentService.countOccupiedSeats(id));
    }

    @GetMapping("/{id}/pricing")
    public CoursePricingResponse getPricing(@PathVariable Long id) {
        var course = courseService.get(id);
        return new CoursePricingResponse(id, course.priceAsMoney());
    }
}