package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForCourseEnrollmentUseCase;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForProgramEnrollmentUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnrollmentAdHocInvoiceController {
    private final CreateAdHocInvoiceForCourseEnrollmentUseCase createCourseAdHocInvoiceUseCase;
    private final CreateAdHocInvoiceForProgramEnrollmentUseCase createProgramAdHocInvoiceUseCase;

    public EnrollmentAdHocInvoiceController(CreateAdHocInvoiceForCourseEnrollmentUseCase createCourseAdHocInvoiceUseCase,
                                            CreateAdHocInvoiceForProgramEnrollmentUseCase createProgramAdHocInvoiceUseCase) {
        this.createCourseAdHocInvoiceUseCase = createCourseAdHocInvoiceUseCase;
        this.createProgramAdHocInvoiceUseCase = createProgramAdHocInvoiceUseCase;
    }

    @PostMapping("/course-enrollments/{id}/invoices/ad-hoc")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "course-enrollment-controller")
    @Operation(operationId = "CourseEnrollment_createAdHocInvoice")
    @RequiresPermission(code = "enrollment.create_ad_hoc_invoice", legacyRoles = "ROLE_ADMINISTRATIVO")
    public InvoiceResponse createCourseAdHocInvoice(@PathVariable Long id, @Valid @RequestBody CreateAdHocInvoiceRequest request,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return InvoiceResponse.from(idempotencyKey == null || idempotencyKey.isBlank()
                ? createCourseAdHocInvoiceUseCase.create(id, request) : createCourseAdHocInvoiceUseCase.create(id, request, idempotencyKey));
    }

    @PostMapping("/program-enrollments/{id}/invoices/ad-hoc")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "program-enrollment-controller")
    @Operation(operationId = "ProgramEnrollment_createAdHocInvoice")
    @RequiresPermission(code = "enrollment.create_ad_hoc_invoice", legacyRoles = "ROLE_ADMINISTRATIVO")
    public InvoiceResponse createProgramAdHocInvoice(@PathVariable Long id, @Valid @RequestBody CreateAdHocInvoiceRequest request,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return InvoiceResponse.from(idempotencyKey == null || idempotencyKey.isBlank()
                ? createProgramAdHocInvoiceUseCase.create(id, request) : createProgramAdHocInvoiceUseCase.create(id, request, idempotencyKey));
    }
}
