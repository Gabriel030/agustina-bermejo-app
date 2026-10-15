package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForCourseEnrollmentUseCase;
import com.red_escuela.backend.enrollment.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.CourseProgressResponse;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.dto.WithdrawEnrollmentRequest;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
public class CourseEnrollmentController {
    private final CourseEnrollmentService courseEnrollmentService;
    private final CurrentPersonService currentPersonService;
    private final CreateAdHocInvoiceForCourseEnrollmentUseCase createAdHocInvoiceUseCase;

    public CourseEnrollmentController(CourseEnrollmentService courseEnrollmentService, CurrentPersonService currentPersonService, CreateAdHocInvoiceForCourseEnrollmentUseCase createAdHocInvoiceUseCase) {
        this.courseEnrollmentService = courseEnrollmentService;
        this.currentPersonService = currentPersonService;
        this.createAdHocInvoiceUseCase = createAdHocInvoiceUseCase;
    }

    @PostMapping("/students/{id}/course-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public CourseEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateCourseEnrollmentRequest request) {
        return CourseEnrollmentResponse.from(courseEnrollmentService.enroll(id, request));
    }

    @PostMapping("/course-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void withdraw(@PathVariable Long id, @Valid @RequestBody(required = false) WithdrawEnrollmentRequest request) {
        LocalDate informedDate = Optional.ofNullable(request).map(WithdrawEnrollmentRequest::effectiveDate).orElse(null);
        courseEnrollmentService.withdraw(id, EnrollmentWithdrawal.voluntaryRegisteredBy(informedDate, currentPersonService.resolveCurrentPerson().getId()));
    }

    @GetMapping("/students/{id}/course-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<CourseEnrollmentResponse> listByStudent(@PathVariable Long id) {
        return courseEnrollmentService.listByStudent(id).stream().map(CourseEnrollmentResponse::from).toList();
    }

    @GetMapping("/course-enrollments/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CourseEnrollmentResponse get(@PathVariable Long id) {
        return CourseEnrollmentResponse.from(courseEnrollmentService.get(id));
    }

    @GetMapping("/course-enrollments/{id}/progress")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CourseProgressResponse getProgress(@PathVariable Long id) {
        return courseEnrollmentService.calculateProgress(id);
    }

    @PostMapping("/course-enrollments/{id}/invoices/ad-hoc")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public InvoiceResponse createAdHocInvoice(@PathVariable Long id, @Valid @RequestBody CreateAdHocInvoiceRequest request,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return InvoiceResponse.from(idempotencyKey == null || idempotencyKey.isBlank()
                ? createAdHocInvoiceUseCase.create(id, request) : createAdHocInvoiceUseCase.create(id, request, idempotencyKey));
    }
}
