package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CoursePricingResponse;
import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.SetCatalogVisibilityRequest;
import com.red_escuela.backend.academicstructure.domain.CatalogArea;
import com.red_escuela.backend.academicstructure.usecase.SetCatalogVisibilityCommand;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.usecase.CreateCourseUseCase;
import com.red_escuela.backend.academicstructure.usecase.DeactivateCourseUseCase;
import com.red_escuela.backend.academicstructure.usecase.GetCourseUseCase;
import com.red_escuela.backend.academicstructure.usecase.GetCoursePricingUseCase;
import com.red_escuela.backend.academicstructure.usecase.ListCoursesUseCase;
import com.red_escuela.backend.academicstructure.usecase.ReactivateCourseUseCase;
import com.red_escuela.backend.academicstructure.usecase.SetCourseCatalogVisibilityUseCase;
import com.red_escuela.backend.academicstructure.usecase.UpdateCourseUseCase;
import com.red_escuela.backend.shared.module.HidesPricesWhenBillingOff;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
@HidesPricesWhenBillingOff
@RequestMapping("/courses")
public class CourseController {

    private final CreateCourseUseCase createCourse;
    private final UpdateCourseUseCase updateCourse;
    private final DeactivateCourseUseCase deactivateCourse;
    private final ReactivateCourseUseCase reactivateCourse;
    private final ListCoursesUseCase listCourses;
    private final GetCourseUseCase getCourse;
    private final GetCoursePricingUseCase getCoursePricing;
    private final SetCourseCatalogVisibilityUseCase setCatalogVisibility;

    public CourseController(CreateCourseUseCase createCourse,
            UpdateCourseUseCase updateCourse,
            DeactivateCourseUseCase deactivateCourse,
            ReactivateCourseUseCase reactivateCourse,
            ListCoursesUseCase listCourses,
            GetCourseUseCase getCourse,
            GetCoursePricingUseCase getCoursePricing,
            SetCourseCatalogVisibilityUseCase setCatalogVisibility) {
        this.createCourse = createCourse;
        this.updateCourse = updateCourse;
        this.deactivateCourse = deactivateCourse;
        this.reactivateCourse = reactivateCourse;
        this.listCourses = listCourses;
        this.getCourse = getCourse;
        this.getCoursePricing = getCoursePricing;
        this.setCatalogVisibility = setCatalogVisibility;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.manage_courses", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CourseResponse create(@Valid @RequestBody CreateCourseRequest request) {
        return createCourse.execute(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(code = "academicstructure.manage_courses", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CourseResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest request) {
        return updateCourse.execute(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_course_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public void deactivate(@PathVariable Long id) {
        deactivateCourse.execute(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_course_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public void reactivate(@PathVariable Long id) {
        reactivateCourse.execute(id);
    }

    @GetMapping
    public List<CourseResponse> list(@RequestParam(required = false) StatusType status,
            @RequestParam(required = false) Boolean visibleInCatalog,
            @RequestParam(required = false) CatalogArea area) {
        return listCourses.execute(status, visibleInCatalog, area);
    }

    @PutMapping("/{id}/catalog-visibility")
    @RequiresPermission(code = "academicstructure.change_catalog_visibility", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CourseResponse setCatalogVisibility(@PathVariable Long id,
            @Valid @RequestBody SetCatalogVisibilityRequest request) {
        return setCatalogVisibility.execute(new SetCatalogVisibilityCommand(id, request.visible(), request.area()));
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable Long id) {
        return getCourse.execute(id);
    }

    @GetMapping("/{id}/pricing")
    public CoursePricingResponse getPricing(@PathVariable Long id) {
        return getCoursePricing.execute(id);
    }
}