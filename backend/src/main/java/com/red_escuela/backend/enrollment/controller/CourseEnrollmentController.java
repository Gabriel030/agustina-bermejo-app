package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForCourseEnrollmentUseCase;
import com.red_escuela.backend.enrollment.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.CourseProgressResponse;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.usecase.courseenrollment.EnrollCourseByStaff;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CourseEnrollmentController {
    private final CourseEnrollmentService courseEnrollmentService;
    private final EnrollCourseByStaff enrollCourseByStaff;
    private final CurrentPersonService currentPersonService;
    private final CreateAdHocInvoiceForCourseEnrollmentUseCase createAdHocInvoiceUseCase;

    public CourseEnrollmentController(CourseEnrollmentService courseEnrollmentService, EnrollCourseByStaff enrollCourseByStaff, CurrentPersonService currentPersonService, CreateAdHocInvoiceForCourseEnrollmentUseCase createAdHocInvoiceUseCase) {
        this.courseEnrollmentService = courseEnrollmentService;
        this.enrollCourseByStaff = enrollCourseByStaff;
        this.currentPersonService = currentPersonService;
        this.createAdHocInvoiceUseCase = createAdHocInvoiceUseCase;
    }

    @PostMapping("/students/{id}/course-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "enrollment.create_course_enrollment", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CourseEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateCourseEnrollmentRequest request) {
        return enrollCourseByStaff.enroll(id, request);
    }

    @GetMapping("/students/{id}/course-enrollments")
    @RequiresPermission(code = "enrollment.read_course_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<CourseEnrollmentResponse> listByStudent(@PathVariable Long id) {
        return courseEnrollmentService.listByStudent(id).stream().map(CourseEnrollmentResponse::from).toList();
    }

    @GetMapping("/course-enrollments/{id}")
    @RequiresPermission(code = "enrollment.read_course_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CourseEnrollmentResponse get(@PathVariable Long id) {
        return CourseEnrollmentResponse.from(courseEnrollmentService.get(id));
    }

    @GetMapping("/course-enrollments/{id}/progress")
    @RequiresPermission(code = "enrollment.read_course_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CourseProgressResponse getProgress(@PathVariable Long id) {
        return courseEnrollmentService.calculateProgress(id);
    }

    @PostMapping("/course-enrollments/{id}/invoices/ad-hoc")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "enrollment.create_ad_hoc_invoice", legacyRoles = "ROLE_ADMINISTRATIVO")
    public InvoiceResponse createAdHocInvoice(@PathVariable Long id, @Valid @RequestBody CreateAdHocInvoiceRequest request,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return InvoiceResponse.from(idempotencyKey == null || idempotencyKey.isBlank()
                ? createAdHocInvoiceUseCase.create(id, request) : createAdHocInvoiceUseCase.create(id, request, idempotencyKey));
    }
}
